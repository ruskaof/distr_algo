package com.ruskaof.algorithm;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ExtendibleHashTable {

    private static final int DEFAULT_BUCKET_CAPACITY = 4;
    private static final int DEFAULT_MAX_KEY_BYTES = 64;
    private static final int DEFAULT_MAX_VALUE_BYTES = 256;

    private final Path baseDir;
    private final int bucketCapacity;
    private final int maxKeyBytes;
    private final int maxValueBytes;

    private static final int META_FILE_SIZE = 16 * 1024 * 1024;
    private static final int META_MAGIC = 0xEAEAEAEA;
    private static final int META_VERSION = 1;

    private int globalDepth;
    private int size;

    private ExtendibleHashTableBucket[] directory;
    private int nextBucketId = 0;

    private FileChannel metaChannel;
    private MappedByteBuffer metaBuffer;

    public ExtendibleHashTable(Path baseDir) {
        this(baseDir, DEFAULT_BUCKET_CAPACITY, DEFAULT_MAX_KEY_BYTES, DEFAULT_MAX_VALUE_BYTES);
    }

    public ExtendibleHashTable(Path baseDir, int bucketCapacity, int maxKeyBytes, int maxValueBytes) {
        if (bucketCapacity <= 0) {
            throw new IllegalArgumentException("Bucket capacity must be positive");
        }
        if (maxKeyBytes <= 0 || maxValueBytes <= 0) {
            throw new IllegalArgumentException("Key/value size limits must be positive");
        }
        this.baseDir = baseDir;
        this.bucketCapacity = bucketCapacity;
        this.maxKeyBytes = maxKeyBytes;
        this.maxValueBytes = maxValueBytes;

        if (!Files.exists(baseDir)) {
            try {
                Files.createDirectories(baseDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create base directory for ExtendibleHashTable: " + baseDir, e);
            }
        }

        initMetadataAndState();
    }

    private ExtendibleHashTableBucket createBucket(int id, int localDepth) {
        Path file = baseDir.resolve("bucket_" + id + ".dat");
        return new ExtendibleHashTableBucket(id, localDepth, bucketCapacity, maxKeyBytes, maxValueBytes, file);
    }

    private void initMetadataAndState() {
        Path metaPath = baseDir.resolve("meta.dat");
        try {
            this.metaChannel = FileChannel.open(
                    metaPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            if (metaChannel.size() < META_FILE_SIZE) {
                metaChannel.truncate(META_FILE_SIZE);
            }
            this.metaBuffer = metaChannel.map(FileChannel.MapMode.READ_WRITE, 0, META_FILE_SIZE);

            int magic = metaBuffer.getInt(0);
            int version = metaBuffer.getInt(4);
            if (magic != META_MAGIC || version != META_VERSION) {
                initEmptyStructure();
                flushMetadata();
                return;
            }

            int storedBucketCapacity = metaBuffer.getInt(8);
            int storedMaxKeyBytes = metaBuffer.getInt(12);
            int storedMaxValueBytes = metaBuffer.getInt(16);

            if (storedBucketCapacity != bucketCapacity ||
                    storedMaxKeyBytes != maxKeyBytes ||
                    storedMaxValueBytes != maxValueBytes) {
                initEmptyStructure();
                flushMetadata();
                return;
            }

            this.globalDepth = metaBuffer.getInt(20);
            this.size = metaBuffer.getInt(24);
            this.nextBucketId = metaBuffer.getInt(28);
            int dirSize = metaBuffer.getInt(32);

            if (globalDepth < 1 || dirSize != (1 << globalDepth)) {
                initEmptyStructure();
                flushMetadata();
                return;
            }

            this.directory = new ExtendibleHashTableBucket[dirSize];
            int offset = 36;
            for (int i = 0; i < dirSize; i++) {
                int bucketId = metaBuffer.getInt(offset + i * 4);
                this.directory[i] = createBucket(bucketId, 1);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize metadata for ExtendibleHashTable", e);
        }
    }

    private void initEmptyStructure() {
        this.globalDepth = 1;
        this.size = 0;
        this.nextBucketId = 1;

        ExtendibleHashTableBucket initial = createBucket(0, 1);
        this.directory = new ExtendibleHashTableBucket[1 << globalDepth];
        for (int i = 0; i < directory.length; i++) {
            directory[i] = initial;
        }
    }

    private void flushMetadata() {
        if (metaBuffer == null) {
            return;
        }
        int dirSize = directory == null ? 0 : directory.length;
        int required = 36 + dirSize * 4;
        if (required > META_FILE_SIZE) {
            throw new IllegalStateException("Metadata file too small for directory of size " + dirSize);
        }

        metaBuffer.position(0);
        metaBuffer.putInt(META_MAGIC);
        metaBuffer.putInt(META_VERSION);
        metaBuffer.putInt(bucketCapacity);
        metaBuffer.putInt(maxKeyBytes);
        metaBuffer.putInt(maxValueBytes);
        metaBuffer.putInt(globalDepth);
        metaBuffer.putInt(size);
        metaBuffer.putInt(nextBucketId);
        metaBuffer.putInt(dirSize);

        int offset = 36;
        for (int i = 0; i < dirSize; i++) {
            int bucketId = directory[i] == null ? -1 : directory[i].id();
            metaBuffer.putInt(offset + i * 4, bucketId);
        }
        metaBuffer.force();
    }

    private int hash(byte[] key) {
        int h = 1;
        for (byte b : key) {
            h = 31 * h + (b & 0xff);
        }
        return h;
    }

    private int indexFor(byte[] key) {
        int h = hash(key);
        int mask = (1 << globalDepth) - 1;
        return h & mask;
    }

    private void doubleDirectory() {
        int oldSize = directory.length;
        ExtendibleHashTableBucket[] old = directory;
        directory = new ExtendibleHashTableBucket[oldSize * 2];
        for (int i = 0; i < oldSize; i++) {
            directory[i] = old[i];
            directory[i + oldSize] = old[i];
        }
        globalDepth++;
        flushMetadata();
    }

    private void splitBucket(int bucketIndex) {
        ExtendibleHashTableBucket bucket = directory[bucketIndex];

        if (bucket.localDepth() == globalDepth) {
            doubleDirectory();
        }

        ExtendibleHashTableBucket oldBucket = bucket;
        int newLocalDepth = oldBucket.localDepth() + 1;
        ExtendibleHashTableBucket newBucket = createBucket(nextBucketId++, newLocalDepth);
        oldBucket.setLocalDepth(newLocalDepth);

        int bit = 1 << (newLocalDepth - 1);

        for (int i = 0; i < directory.length; i++) {
            if (directory[i] == oldBucket) {
                if ((i & bit) != 0) {
                    directory[i] = newBucket;
                } else {
                    directory[i] = oldBucket;
                }
            }
        }

        oldBucket.redistributeEntries(this);
        flushMetadata();
    }

    void insertDuringSplit(byte[] key, byte[] value) {
        int index = indexFor(key);
        ExtendibleHashTableBucket target = directory[index];
        boolean inserted = target.put(key, value);
        if (!inserted) {
            throw new IllegalStateException("Bucket overflow during split redistribution");
        }
    }

    public int size() {
        return size;
    }

    public byte[] get(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("Null keys are not supported");
        }
        int index = indexFor(key);
        ExtendibleHashTableBucket bucket = directory[index];
        return bucket.get(key);
    }

    public void put(byte[] key, byte[] value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Null keys or values are not supported");
        }

        while (true) {
            int index = indexFor(key);
            ExtendibleHashTableBucket bucket = directory[index];
            byte[] existing = bucket.get(key);

            if (existing != null) {
                bucket.put(key, value);
                flushMetadata();
                return;
            }

            boolean inserted = bucket.put(key, value);
            if (inserted) {
                size++;
                flushMetadata();
                return;
            }
            splitBucket(index);
        }
    }

    public byte[] remove(byte[] key) {
        if (key == null) {
            throw new IllegalArgumentException("Null keys are not supported");
        }
        int index = indexFor(key);
        ExtendibleHashTableBucket bucket = directory[index];
        byte[] old = bucket.remove(key);
        if (old != null) {
            size--;
            flushMetadata();
        }
        return old;
    }

    public void close() {
        if (directory == null) {
            return;
        }
        for (ExtendibleHashTableBucket b : directory) {
            if (b != null) {
                b.close();
            }
        }
        if (metaChannel != null) {
            try {
                metaChannel.close();
            } catch (IOException ignored) {
            } finally {
                metaChannel = null;
                metaBuffer = null;
            }
        }
    }

    int getGlobalDepth() {
        return globalDepth;
    }

    int getDirectorySize() {
        return directory == null ? 0 : directory.length;
    }

    int getBucketIdForIndex(int index) {
        if (directory == null || index < 0 || index >= directory.length) {
            throw new IndexOutOfBoundsException("Index " + index);
        }
        return directory[index].id();
    }

    int getBucketLocalDepthForIndex(int index) {
        if (directory == null || index < 0 || index >= directory.length) {
            throw new IndexOutOfBoundsException("Index " + index);
        }
        return directory[index].localDepth();
    }

}