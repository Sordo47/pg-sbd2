package pl.edu.pg.eti.sbd;

public class Index {
    int firstKey;
    int position;

    public Index(int firstKey, int position) {
        this.firstKey = firstKey;
        this.position = position;
    }

    public int getFirstKey() {
        return firstKey;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public void setFirstKey(int firstKey) {
        this.firstKey = firstKey;
    }

    public static Index byteToIndex(byte[] buff) {
        String buff_converted = new String(buff);
        String[] parts = buff_converted.split("[ \u0000]");
        if (parts.length < 2) return null;
        int key = Integer.parseInt(parts[0]);
        int pos = Integer.parseInt(parts[1]);
        return new Index(key, pos);
    }
}
