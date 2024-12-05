package pl.edu.pg.eti.sbd;

public class Record {
    int key;
    String text;
    int pointer;

    public Record(int key, String text, int pointer) {
        this.key = key;
        this.text = text;
        this.pointer = pointer;
    }

    public int getKey() {
        return key;
    }

    public void setKey(int key) {
        this.key = key;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getPointer() {
        return pointer;
    }

    public void setPointer(int pointer) {
        this.pointer = pointer;
    }

    public static Record byteToRecord(byte[] buff) {
        String buff_converted = new String(buff);
        String[] parts = buff_converted.split("[ \u0000]");
        if (parts.length < 3) return null;
        int key = Integer.parseInt(parts[0]);
        int ptr = Integer.parseInt(parts[2]);
        return new Record(key, parts[1], ptr);
    }

    public byte[] toByte() {
        byte[] result = new byte[Structure.DATA_ENT_SIZE];
        String r1 = String.valueOf(this.key);
        String r2 = String.valueOf(this.pointer);
        String total = r1+" "+this.text+" "+r2;
        byte[] temp = total.getBytes();
        System.arraycopy(temp, 0, result, 0, temp.length);
        return result;
    }

    @Override
    public String toString() {
        return "Record{" +
                "key=" + key +
                ", text='" + text + '\'' +
                ", pointer=" + pointer +
                '}';
    }
}
