package jp.azisaba.lgw.ecplus.utils.nbt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class Nbt {

    public static final byte TAG_END = 0;
    public static final byte TAG_BYTE = 1;
    public static final byte TAG_SHORT = 2;
    public static final byte TAG_INT = 3;
    public static final byte TAG_LONG = 4;
    public static final byte TAG_FLOAT = 5;
    public static final byte TAG_DOUBLE = 6;
    public static final byte TAG_BYTE_ARRAY = 7;
    public static final byte TAG_STRING = 8;
    public static final byte TAG_LIST = 9;
    public static final byte TAG_COMPOUND = 10;
    public static final byte TAG_INT_ARRAY = 11;
    public static final byte TAG_LONG_ARRAY = 12;

    private Nbt() {}

    public interface Tag {
        byte getTypeId();
        Tag copy();
    }

    public static final class ByteTag implements Tag {
        private final byte value;
        public ByteTag(byte value) { this.value = value; }
        public byte getValue() { return value; }
        @Override public byte getTypeId() { return TAG_BYTE; }
        @Override public Tag copy() { return new ByteTag(value); }
    }

    public static final class ShortTag implements Tag {
        private final short value;
        public ShortTag(short value) { this.value = value; }
        public short getValue() { return value; }
        @Override public byte getTypeId() { return TAG_SHORT; }
        @Override public Tag copy() { return new ShortTag(value); }
    }

    public static final class IntTag implements Tag {
        private final int value;
        public IntTag(int value) { this.value = value; }
        public int getValue() { return value; }
        @Override public byte getTypeId() { return TAG_INT; }
        @Override public Tag copy() { return new IntTag(value); }
    }

    public static final class LongTag implements Tag {
        private final long value;
        public LongTag(long value) { this.value = value; }
        public long getValue() { return value; }
        @Override public byte getTypeId() { return TAG_LONG; }
        @Override public Tag copy() { return new LongTag(value); }
    }

    public static final class FloatTag implements Tag {
        private final float value;
        public FloatTag(float value) { this.value = value; }
        public float getValue() { return value; }
        @Override public byte getTypeId() { return TAG_FLOAT; }
        @Override public Tag copy() { return new FloatTag(value); }
    }

    public static final class DoubleTag implements Tag {
        private final double value;
        public DoubleTag(double value) { this.value = value; }
        public double getValue() { return value; }
        @Override public byte getTypeId() { return TAG_DOUBLE; }
        @Override public Tag copy() { return new DoubleTag(value); }
    }

    public static final class ByteArrayTag implements Tag {
        private final byte[] value;
        public ByteArrayTag(byte[] value) { this.value = value; }
        public byte[] getValue() { return value; }
        @Override public byte getTypeId() { return TAG_BYTE_ARRAY; }
        @Override public Tag copy() { return new ByteArrayTag(Arrays.copyOf(value, value.length)); }
    }

    public static final class StringTag implements Tag {
        private final String value;
        public StringTag(String value) { this.value = value != null ? value : ""; }
        public String getValue() { return value; }
        @Override public byte getTypeId() { return TAG_STRING; }
        @Override public Tag copy() { return new StringTag(value); }
    }

    public static final class ListTag implements Tag {
        private byte elementTypeId;
        private final List<Tag> elements;

        public ListTag(byte elementTypeId, List<Tag> elements) {
            this.elementTypeId = elementTypeId;
            this.elements = elements != null ? elements : new ArrayList<>();
        }

        public ListTag() {
            this(TAG_END, new ArrayList<>());
        }

        public byte getElementTypeId() { return elementTypeId; }
        public List<Tag> getElements() { return elements; }
        public int size() { return elements.size(); }
        public boolean isEmpty() { return elements.isEmpty(); }

        public void add(Tag tag) {
            if (elements.isEmpty()) {
                this.elementTypeId = tag.getTypeId();
            }
            elements.add(tag);
        }

        @Override public byte getTypeId() { return TAG_LIST; }
        @Override
        public Tag copy() {
            List<Tag> copyList = new ArrayList<>(elements.size());
            for (Tag tag : elements) copyList.add(tag.copy());
            return new ListTag(elementTypeId, copyList);
        }
    }

    public static final class IntArrayTag implements Tag {
        private final int[] value;
        public IntArrayTag(int[] value) { this.value = value; }
        public int[] getValue() { return value; }
        @Override public byte getTypeId() { return TAG_INT_ARRAY; }
        @Override public Tag copy() { return new IntArrayTag(Arrays.copyOf(value, value.length)); }
    }

    public static final class LongArrayTag implements Tag {
        private final long[] value;
        public LongArrayTag(long[] value) { this.value = value; }
        public long[] getValue() { return value; }
        @Override public byte getTypeId() { return TAG_LONG_ARRAY; }
        @Override public Tag copy() { return new LongArrayTag(Arrays.copyOf(value, value.length)); }
    }

    public static final class CompoundTag implements Tag {
        private final Map<String, Tag> tags = new LinkedHashMap<>();

        public Map<String, Tag> getTags() { return tags; }

        public Tag get(String key) { return tags.get(key); }
        public void put(String key, Tag tag) { tags.put(key, tag); }
        public void remove(String key) { tags.remove(key); }

        public CompoundTag getCompound(String key) {
            Tag tag = tags.get(key);
            return tag instanceof CompoundTag ? (CompoundTag) tag : null;
        }

        public ListTag getList(String key) {
            Tag tag = tags.get(key);
            return tag instanceof ListTag ? (ListTag) tag : null;
        }

        public String getString(String key) {
            Tag tag = tags.get(key);
            return tag instanceof StringTag ? ((StringTag) tag).getValue() : null;
        }

        public Number getNumber(String key) {
            Tag tag = tags.get(key);
            if (tag instanceof ByteTag) return ((ByteTag) tag).getValue();
            if (tag instanceof ShortTag) return ((ShortTag) tag).getValue();
            if (tag instanceof IntTag) return ((IntTag) tag).getValue();
            if (tag instanceof LongTag) return ((LongTag) tag).getValue();
            if (tag instanceof FloatTag) return ((FloatTag) tag).getValue();
            if (tag instanceof DoubleTag) return ((DoubleTag) tag).getValue();
            return null;
        }

        public void putByte(String key, byte value) { tags.put(key, new ByteTag(value)); }
        public void putShort(String key, short value) { tags.put(key, new ShortTag(value)); }
        public void putInt(String key, int value) { tags.put(key, new IntTag(value)); }
        public void putLong(String key, long value) { tags.put(key, new LongTag(value)); }
        public void putString(String key, String value) { tags.put(key, new StringTag(value)); }

        @Override public byte getTypeId() { return TAG_COMPOUND; }
        @Override
        public CompoundTag copy() {
            CompoundTag copy = new CompoundTag();
            for (Map.Entry<String, Tag> entry : tags.entrySet()) {
                copy.put(entry.getKey(), entry.getValue().copy());
            }
            return copy;
        }
    }

    public static CompoundTag readCompressed(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(data)))) {
            byte typeId = in.readByte();
            if (typeId != TAG_COMPOUND) {
                throw new IOException("Root tag is not TAG_Compound: " + typeId);
            }
            in.readUTF(); // Root tag name (typically empty)
            return (CompoundTag) readTag(TAG_COMPOUND, in);
        }
    }

    public static byte[] writeCompressed(CompoundTag compound) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(TAG_COMPOUND);
            out.writeUTF(""); // Root tag name
            writeTagPayload(compound, out);
        }
        return bytes.toByteArray();
    }

    private static Tag readTag(byte typeId, DataInputStream in) throws IOException {
        switch (typeId) {
            case TAG_END:
                return null;
            case TAG_BYTE:
                return new ByteTag(in.readByte());
            case TAG_SHORT:
                return new ShortTag(in.readShort());
            case TAG_INT:
                return new IntTag(in.readInt());
            case TAG_LONG:
                return new LongTag(in.readLong());
            case TAG_FLOAT:
                return new FloatTag(in.readFloat());
            case TAG_DOUBLE:
                return new DoubleTag(in.readDouble());
            case TAG_BYTE_ARRAY: {
                int len = in.readInt();
                byte[] b = new byte[len];
                in.readFully(b);
                return new ByteArrayTag(b);
            }
            case TAG_STRING:
                return new StringTag(in.readUTF());
            case TAG_LIST: {
                byte elemType = in.readByte();
                int len = in.readInt();
                List<Tag> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) {
                    list.add(readTag(elemType, in));
                }
                return new ListTag(elemType, list);
            }
            case TAG_COMPOUND: {
                CompoundTag comp = new CompoundTag();
                while (true) {
                    byte t = in.readByte();
                    if (t == TAG_END) break;
                    String name = in.readUTF();
                    comp.put(name, readTag(t, in));
                }
                return comp;
            }
            case TAG_INT_ARRAY: {
                int len = in.readInt();
                int[] a = new int[len];
                for (int i = 0; i < len; i++) a[i] = in.readInt();
                return new IntArrayTag(a);
            }
            case TAG_LONG_ARRAY: {
                int len = in.readInt();
                long[] a = new long[len];
                for (int i = 0; i < len; i++) a[i] = in.readLong();
                return new LongArrayTag(a);
            }
            default:
                throw new IOException("Unknown tag type: " + typeId);
        }
    }

    private static void writeTagPayload(Tag tag, DataOutputStream out) throws IOException {
        switch (tag.getTypeId()) {
            case TAG_BYTE:
                out.writeByte(((ByteTag) tag).getValue());
                break;
            case TAG_SHORT:
                out.writeShort(((ShortTag) tag).getValue());
                break;
            case TAG_INT:
                out.writeInt(((IntTag) tag).getValue());
                break;
            case TAG_LONG:
                out.writeLong(((LongTag) tag).getValue());
                break;
            case TAG_FLOAT:
                out.writeFloat(((FloatTag) tag).getValue());
                break;
            case TAG_DOUBLE:
                out.writeDouble(((DoubleTag) tag).getValue());
                break;
            case TAG_BYTE_ARRAY: {
                byte[] b = ((ByteArrayTag) tag).getValue();
                out.writeInt(b.length);
                out.write(b);
                break;
            }
            case TAG_STRING:
                out.writeUTF(((StringTag) tag).getValue());
                break;
            case TAG_LIST: {
                ListTag list = (ListTag) tag;
                out.writeByte(list.getElementTypeId());
                out.writeInt(list.size());
                for (Tag elem : list.getElements()) {
                    writeTagPayload(elem, out);
                }
                break;
            }
            case TAG_COMPOUND: {
                CompoundTag comp = (CompoundTag) tag;
                for (Map.Entry<String, Tag> entry : comp.getTags().entrySet()) {
                    out.writeByte(entry.getValue().getTypeId());
                    out.writeUTF(entry.getKey());
                    writeTagPayload(entry.getValue(), out);
                }
                out.writeByte(TAG_END);
                break;
            }
            case TAG_INT_ARRAY: {
                int[] a = ((IntArrayTag) tag).getValue();
                out.writeInt(a.length);
                for (int v : a) out.writeInt(v);
                break;
            }
            case TAG_LONG_ARRAY: {
                long[] a = ((LongArrayTag) tag).getValue();
                out.writeInt(a.length);
                for (long v : a) out.writeLong(v);
                break;
            }
            default:
                throw new IOException("Unknown tag type: " + tag.getTypeId());
        }
    }
}
