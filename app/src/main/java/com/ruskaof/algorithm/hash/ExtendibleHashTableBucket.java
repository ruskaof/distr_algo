package com.ruskaof.algorithm.hash;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class ExtendibleHashTableBucket {

    // [0..3] int localDepth
    // [4..7] int bucketCapacity
    // [8..] fixed-size entry records
    //
    // records:
    // [0..3] int status (0 = empty, 1 = used)
    // [4..7] int keyLen
    // [8..11] int valueLen
    // [...] key bytes (fixed maxKeyBytes)
    // [...] value bytes (fixed maxValueBytes)

    private final int id;
    private int localDepth;
    private final int bucketCapacity;
    private final int maxKeyBytes;
    private final int maxValueBytes;
    private final int entryRecordSize;
    private final FileChannel channel;
    private final MappedByteBuffer buffer;
    private boolean initializedOnCreate = false;

    ExtendibleHashTableBucket(int id,
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
                    StandardOpenOption.WRITE);

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
                initializedOnCreate = true;
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
    }

    boolean needsFlushOnCreate() {
        return initializedOnCreate;
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

    boolean put(byte[] key, byte[] value) {
        if (key.length > maxKeyBytes) {
            throw new IllegalArgumentException("Key too large: " + key.length + " > " + maxKeyBytes);
        }
        if (value.length > maxValueBytes) {
            throw new IllegalArgumentException("Value too large: " + value.length + " > " + maxValueBytes);
        }

        int idx = findEntryIndex(key);
        if (idx >= 0) {
            writeEntry(idx, key, value);
            return false;
        }

        int free = findFreeIndex();
        if (free < 0) {
            return false;
        }

        writeEntry(free, key, value);
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
