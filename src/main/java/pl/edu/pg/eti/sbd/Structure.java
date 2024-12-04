package pl.edu.pg.eti.sbd;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Structure {

    static final String PATH_INDEX = "index.txt";
    static final String PATH_MAIN = "data.txt";
    static final String PATH_OF = "overflow.txt";
    static final int PAGE_INDEX = 4;
    static final int PAGE_MAIN = 4;

    static final int INDEX_ENT_SIZE = 20;
    static final int DATA_ENT_SIZE = 30 + 2 + 9 + 9;
    RandomAccessFile index, main, overflow;

    int indexCt;
    int recordCt;
    int reads;
    int saves;
    public Structure() {
        try {
            this.index = new RandomAccessFile(PATH_INDEX, "rw");
            this.main = new RandomAccessFile(PATH_MAIN, "rw");
            this.overflow = new RandomAccessFile(PATH_OF, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        // count indexes
        this.indexCt = 0;
        Index ind;
        do {
            byte[] tmp_ind = new byte[INDEX_ENT_SIZE];
            try {
                this.index.seek((long) this.indexCt * INDEX_ENT_SIZE);
                this.index.read(tmp_ind);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            ind = Index.byteToIndex(tmp_ind);
            if (ind != null) {
                this.indexCt++;
            }

        } while (ind!=null);

        // TODO count records
        this.recordCt = 0;
        for (int i=0; i<this.indexCt; ++i) {
            byte[] tmp_page = new byte[PAGE_MAIN*DATA_ENT_SIZE];
            try {
                this.main.seek((long) i * PAGE_MAIN * DATA_ENT_SIZE);
                this.main.read(tmp_page);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

            for (int j=0; j<PAGE_MAIN; ++j) {
                byte[] tmp_record = new byte[DATA_ENT_SIZE];
                System.arraycopy(tmp_page, j*DATA_ENT_SIZE, tmp_record, 0, DATA_ENT_SIZE);
                Record rec = Record.byteToRecord(tmp_record);
                if (rec != null) {
                    this.recordCt++;
                }
            }
        }

        this.reads = 0;
        this.saves = 0;
    }

    public boolean insertRecord(int key, String val) {
        int pageNo = getPage(key);
        if (pageNo == -1) {
            // TODO nasz klucz jest przed pierwszym indeksem
        }
        // przeszukanie strony z rekordami
        byte[] page_buffer = new byte[PAGE_MAIN*DATA_ENT_SIZE];
        Record[] page = new Record[PAGE_MAIN];
        try {
            main.seek((long) pageNo *PAGE_MAIN*DATA_ENT_SIZE);
            main.read(page_buffer);
            reads++;
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (int i=0; i<PAGE_MAIN; ++i) {
            // tworzenie bufora na pojedynczy rekord
            byte[] single_record_buf = new byte[DATA_ENT_SIZE];
            System.arraycopy(page_buffer, i*DATA_ENT_SIZE, single_record_buf, 0, DATA_ENT_SIZE);
            // parsowanie i wpisanie rekordu na strone
            page[i] = Record.byteToRecord(single_record_buf);
        }

        // szukanie wolnego miejsca na stronie
        for (int i=0; i<PAGE_MAIN; ++i) {
            if (page[i] == null) {
                // wolne miejsce
                page[i] = new Record(key, val, -1);
                // sortowanie strony
                for (int j=i-1; j>=0; --j) {
                    if (page[j].getKey() > key) {
                        Record tmp = page[j];
                        page[j] = page[j+1];
                        page[j+1] = tmp;
                    }
                }
                this.recordCt++;
                // zapis nowej strony
                savePage(page, pageNo);
                return true;
            }
            if (page[i].getKey() == key) {
                // znaleziono identyczny klucz!
                // koniec funkcji - przekazanie wyniku niżej
                return false;
            }
        }
        return false;
    }

    private int getPage(int searched_key) {
        int indexPages = (int) Math.ceil((double)indexCt / PAGE_INDEX);
        int p = 0;
        int k = indexPages;
        int m = (p+k)/2;
        boolean finished = false;
        Index[] index_page = new Index[PAGE_INDEX];
        Index next_index = null;
        int found_position = -1;
        while (!finished) {
            // tworzenie bufora strony indeksów
            byte[] index_buf = new byte[(PAGE_INDEX+1)*INDEX_ENT_SIZE];
            try {
                // ładowanie odpowiedniej strony indesków do pamięci głównej
                index.seek((long) m *PAGE_INDEX*INDEX_ENT_SIZE);
                index.read(index_buf);
                reads++;
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }

            // rozdzielanie bufora indeksów i wpisanie do strony
            for (int i=0; i<PAGE_INDEX+1; ++i) {
                // tworzenie bufora na pojedynczy indeks
                byte[] single_index_buf = new byte[INDEX_ENT_SIZE];
                System.arraycopy(index_buf, i*INDEX_ENT_SIZE, single_index_buf, 0, INDEX_ENT_SIZE);
                // parsowanie i wpisanie indeksu na strone
                if (i < PAGE_INDEX) {
                    index_page[i] = Index.byteToIndex(single_index_buf);
                }
                else {
                    next_index = Index.byteToIndex(single_index_buf);
                }
            }

            if ((index_page[0] != null) && (index_page[0].getFirstKey() > searched_key)) {
                // szukamy po lewej stronie strony indeksów
                k = m - 1;
                m = (p+k)/2;
                // TODO co jak poniżej pierwszego indeksu?
            }
            // przeszukanie strony
            for (int i=1; i<PAGE_INDEX; ++i) {
                if (index_page[i] == null || index_page[i].getFirstKey() > searched_key) {
                    // znaleziono odpowiedni index
                    assert index_page[i - 1] != null;
                    found_position = index_page[i-1].getPosition();
                    finished = true;
                    break;
                }
            }
            if (finished) break;
            if (next_index == null || next_index.getFirstKey() > searched_key) {
                // odpowiedni index to ostatni w stronie
                found_position = index_page[PAGE_INDEX-1].getPosition();
                finished = true;
            }
            else {
                // szukamy po prawej stronie strony
                p = m + 1;
                m = (p + k) / 2;
            }
        }
        return found_position;
    }

    private void savePage(Record[] page, int pageNo) {
        byte[] buffer = new byte[PAGE_MAIN*DATA_ENT_SIZE];
        for (int i=0; i<page.length; ++i) {
            byte[] buf_record = new byte[DATA_ENT_SIZE];
            if (page[i] != null) buf_record = page[i].toByte();
            System.arraycopy(buf_record, 0, buffer, i*DATA_ENT_SIZE, DATA_ENT_SIZE);
        }
        try {
            this.main.seek((long) pageNo * PAGE_MAIN * DATA_ENT_SIZE);
            this.main.write(buffer);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
