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
    private static final int META_MAGIC = 0xE17E1DAB;
    private static final int META_VERSION = 1;

    private int globalDepth;
    private int size;

    private Bucket[] directory;
    private int nextBucketId = 0;

    private FileChannel metaChannel;
    private MappedByteBuffer metaBuffer;

    private static final class Bucket {
        // File layout:
        // [0..3]   int localDepth
        // [4..7]   int bucketCapacity
        // [8..]    fixed-size entry records
        //
        // Each entry record:
        // [0..3]   int status (0 = empty, 1 = used)
        // [4..7]   int keyLen
        // [8..11]  int valueLen
        // [...]    key bytes (fixed maxKeyBytes)
        // [...]    value bytes (fixed maxValueBytes)

        private final int id;
        private int localDepth;
        private final int bucketCapacity;
        private final int maxKeyBytes;
        private final int maxValueBytes;
        private final int entryRecordSize;
        private final FileChannel channel;
        private final MappedByteBuffer buffer;

        Bucket(int id,
               int localDepth,
               int bucketCapacity,
               int maxKeyBytes,
               int maxValueBytes,
               Path file) {
            this.id = id;
            this.localDepth = localDepth;
            this.bucketCapacity = bucketCapacity;
            this.maxKeyBytes = maxKeyBytes;
            this.maxValueBytes = maxValueBytes;
            this.entryRecordSize = 12 + maxKeyBytes + maxValueBytes;

            try {
                if (!Files.exists(file.getParent())) {
                    Files.createDirectories(file.getParent());
                }
                this.channel = FileChannel.open(
                        file,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE
                );

                long requiredSize = headerSize() + (long) bucketCapacity * entryRecordSize;
                if (channel.size() < requiredSize) {
                    channel.truncate(requiredSize);
                }

                this.buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, requiredSize);

                if (readInt(0) == 0 && readInt(4) == 0) {
                    // Uninitialized bucket file – write header and clear entries.
                    writeInt(0, localDepth);
                    writeInt(4, bucketCapacity);
                    clearAllEntries();
                } else {
                    // Existing bucket – load header values.
                    this.localDepth = readInt(0);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize bucket file: " + file, e);
            }
        }

        int headerSize() {
            return 8;
        }

        int id() {
            return id;
        }

        int localDepth() {
            return localDepth;
        }

        void setLocalDepth(int newDepth) {
            this.localDepth = newDepth;
            writeInt(0, newDepth);
            force();
        }

        private int entryOffset(int index) {
            return headerSize() + index * entryRecordSize;
        }

        private int readInt(int offset) {
            return buffer.getInt(offset);
        }

        private void writeInt(int offset, int value) {
            buffer.putInt(offset, value);
        }

        private void clearAllEntries() {
            for (int i = 0; i < bucketCapacity; i++) {
                int off = entryOffset(i);
                writeInt(off, 0); // status = 0 (empty)
            }
            force();
        }

        private boolean keysEqual(int off, byte[] key) {
            int keyLen = buffer.getInt(off + 4);
            if (keyLen != key.length) {
                return false;
            }
            int keyStart = off + 12;
            for (int i = 0; i < keyLen; i++) {
                if (buffer.get(keyStart + i) != key[i]) {
                    return false;
                }
            }
            return true;
        }

        private int findEntryIndex(byte[] key) {
            for (int i = 0; i < bucketCapacity; i++) {
                int off = entryOffset(i);
                int status = buffer.getInt(off);
                if (status == 0) {
                    continue;
                }
                if (keysEqual(off, key)) {
                    return i;
                }
            }
            return -1;
        }

        private int findFreeIndex() {
            for (int i = 0; i < bucketCapacity; i++) {
                int off = entryOffset(i);
                int status = buffer.getInt(off);
                if (status == 0) {
                    return i;
                }
            }
            return -1;
        }

        byte[] get(byte[] key) {
            int idx = findEntryIndex(key);
            if (idx < 0) {
                return null;
            }
            int off = entryOffset(idx);
            int valueLen = buffer.getInt(off + 8);
            int valueStart = off + 12 + maxKeyBytes;
            byte[] value = new byte[valueLen];
            for (int i = 0; i < valueLen; i++) {
                value[i] = buffer.get(valueStart + i);
            }
            return value;
        }

        boolean insertOrUpdate(byte[] key, byte[] value) {
            if (key.length > maxKeyBytes) {
                throw new IllegalArgumentException("Key too large: " + key.length + " > " + maxKeyBytes);
            }
            if (value.length > maxValueBytes) {
                throw new IllegalArgumentException("Value too large: " + value.length + " > " + maxValueBytes);
            }

            int idx = findEntryIndex(key);
            if (idx >= 0) {
                writeEntry(idx, key, value);
                force();
                return false;
            }

            int free = findFreeIndex();
            if (free < 0) {
                return false;
            }

            writeEntry(free, key, value);
            force();
            return true;
        }

        byte[] remove(byte[] key) {
            int idx = findEntryIndex(key);
            if (idx < 0) {
                return null;
            }
            int off = entryOffset(idx);
            int valueLen = buffer.getInt(off + 8);
            int valueStart = off + 12 + maxKeyBytes;
            byte[] value = new byte[valueLen];
            for (int i = 0; i < valueLen; i++) {
                value[i] = buffer.get(valueStart + i);
            }

            // Mark as empty
            writeInt(off, 0);
            force();
            return value;
        }

        void writeEntry(int index, byte[] key, byte[] value) {
            int off = entryOffset(index);
            writeInt(off, 1); // status = used
            writeInt(off + 4, key.length);
            writeInt(off + 8, value.length);

            int keyStart = off + 12;
            for (int i = 0; i < maxKeyBytes; i++) {
                byte b = i < key.length ? key[i] : 0;
                buffer.put(keyStart + i, b);
            }

            int valueStart = keyStart + maxKeyBytes;
            for (int i = 0; i < maxValueBytes; i++) {
                byte b = i < value.length ? value[i] : 0;
                buffer.put(valueStart + i, b);
            }
        }

        void redistributeEntries(ExtendibleHashTable table) {
            for (int i = 0; i < bucketCapacity; i++) {
                int off = entryOffset(i);
                int status = buffer.getInt(off);
                if (status == 0) {
                    continue;
                }
                int keyLen = buffer.getInt(off + 4);
                int valueLen = buffer.getInt(off + 8);
                int keyStart = off + 12;
                int valueStart = keyStart + maxKeyBytes;
                byte[] key = new byte[keyLen];
                byte[] value = new byte[valueLen];
                for (int j = 0; j < keyLen; j++) {
                    key[j] = buffer.get(keyStart + j);
                }
                for (int j = 0; j < valueLen; j++) {
                    value[j] = buffer.get(valueStart + j);
                }
                writeInt(off, 0);
                table.insertDuringSplit(key, value);
            }
            force();
        }

        void force() {
            buffer.force();
        }

        void close() {
            try {
                channel.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

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

    private Bucket createBucket(int id, int localDepth) {
        Path file = baseDir.resolve("bucket_" + id + ".dat");
        return new Bucket(id, localDepth, bucketCapacity, maxKeyBytes, maxValueBytes, file);
    }

    private void initMetadataAndState() {
        Path metaPath = baseDir.resolve("meta.dat");
        try {
            this.metaChannel = FileChannel.open(
                    metaPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE
            );
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

            this.directory = new Bucket[dirSize];
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

        Bucket initial = createBucket(0, 1);
        this.directory = new Bucket[1 << globalDepth];
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
        Bucket[] old = directory;
        directory = new Bucket[oldSize * 2];
        for (int i = 0; i < oldSize; i++) {
            directory[i] = old[i];
            directory[i + oldSize] = old[i];
        }
        globalDepth++;
        flushMetadata();
    }

    private void splitBucket(int bucketIndex) {
        Bucket bucket = directory[bucketIndex];

        if (bucket.localDepth() == globalDepth) {
            doubleDirectory();
        }

        Bucket oldBucket = bucket;
        int newLocalDepth = oldBucket.localDepth() + 1;
        Bucket newBucket = createBucket(nextBucketId++, newLocalDepth);
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

    private void insertDuringSplit(byte[] key, byte[] value) {
        int index = indexFor(key);
        Bucket target = directory[index];
        boolean inserted = target.insertOrUpdate(key, value);
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
        Bucket bucket = directory[index];
        return bucket.get(key);
    }

    public void put(byte[] key, byte[] value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Null keys or values are not supported");
        }

        while (true) {
            int index = indexFor(key);
            Bucket bucket = directory[index];
            byte[] existing = bucket.get(key);

            if (existing != null) {
                bucket.insertOrUpdate(key, value);
                flushMetadata();
                return;
            }

            boolean inserted = bucket.insertOrUpdate(key, value);
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
        Bucket bucket = directory[index];
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
        for (Bucket b : directory) {
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

    public void putString(String key, String value) {
        put(key.getBytes(), value.getBytes());
    }

    public String getString(String key) {
        byte[] value = get(key.getBytes());
        return value == null ? null : new String(value);
    }

    public String removeString(String key) {
        byte[] value = remove(key.getBytes());
        return value == null ? null : new String(value);
    }
}