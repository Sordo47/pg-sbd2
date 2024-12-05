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

    public byte[] toByte() {
        byte[] result = new byte[Structure.INDEX_ENT_SIZE];
        String r1 = String.valueOf(this.firstKey);
        String r2 = String.valueOf(this.position);
        String total = r1+" "+r2;
        byte[] temp = total.getBytes();
        System.arraycopy(temp, 0, result, 0, temp.length);
        return result;
    }

    @Override
    public String toString() {
        return "Index{" +
                "firstKey=" + firstKey +
                ", position=" + position +
                '}';
    }
}
