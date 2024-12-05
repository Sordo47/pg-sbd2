package pl.edu.pg.eti.sbd;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class Structure {

    static final String PATH_INDEX = "index.txt";
    static final String PATH_MAIN = "data.txt";
    static final String PATH_OF = "overflow.txt";
    static final int PAGE_INDEX = 4;
    static final int PAGE_MAIN = 4;

    static final int INDEX_ENT_SIZE = 20;
    static final int DATA_ENT_SIZE = 30 + 2 + 9 + 9;

    static final double ALPHA = 0.5;
    RandomAccessFile index, main, overflow;

    int indexCt;
    int recordCt;
    int overflowCt;
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

        // count records
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

        // count overflow
        this.overflowCt = 0;
        Record of_rec;
        do {
            byte[] tmp_rec = new byte[DATA_ENT_SIZE];
            try {
                this.overflow.seek((long) this.overflowCt * DATA_ENT_SIZE);
                this.overflow.read(tmp_rec);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
            of_rec = Record.byteToRecord(tmp_rec);
            if (of_rec != null) {
                this.overflowCt++;
            }

        } while (of_rec!=null);

        this.reads = 0;
        this.saves = 0;
    }

    public boolean insertRecord(int key, String val) {
        int pageNo = getPage(key);
        if (pageNo == -1) {
            // nasz klucz jest przed pierwszym indeksem
            // wrzut na overflow z pointerem -2
            Record toInsert = new Record(key, val, -2);
            appendOverflow(toInsert);
            // TODO obligatoryjna reorganizacja!
        }
        // przeszukanie strony z rekordami
        Record[] page = readPage(pageNo, main);

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
                savePage(page, pageNo, main);
                return true;
            }
            if (page[i].getKey() == key) {
                // znaleziono identyczny klucz!
                // koniec funkcji - przekazanie wyniku niżej
                return false;
            }
        }

        // występuje overflow
        int overflowEntry;
        // przeszukanie gdzie włożyć do overflow
        for (overflowEntry=0; overflowEntry<PAGE_MAIN-1; ++overflowEntry) {
            assert page[overflowEntry+1] != null;
            if (page[overflowEntry+1].getKey() > key ) {
                break;
            }
        }

        // znajdowanie odpowiedniego miejsca w łańcuchu overflow
        assert page[overflowEntry] != null;
        Record chainEnd = page[overflowEntry];
        Record prevChain = page[overflowEntry];
        Record[] chainEndPage = new Record[PAGE_MAIN];
        Record[] prevChainPage = new Record[PAGE_MAIN];
        int prevPointer = -1;
        while (chainEnd.getPointer() != -1 && chainEnd.getKey() < key) {
            // odczyt z overflow
            prevChainPage = chainEndPage;
            if (chainEnd == page[overflowEntry] || (chainEnd.getPointer() / PAGE_MAIN) != (prevChain.getPointer() / PAGE_MAIN)) {
                // jeżeli kolejny element łańcucha jest na innej stronie overflow
                chainEndPage = readPage(chainEnd.getPointer() / PAGE_MAIN, overflow);
            }
            prevPointer = prevChain.getPointer();
            prevChain = chainEnd;
            chainEnd = chainEndPage[chainEnd.getPointer() - ((chainEnd.getPointer() / PAGE_MAIN) * PAGE_MAIN)];

            assert chainEnd != null;
            if (chainEnd.getKey() == key) {
                // znaleziono ten sam klucz
                return false;
            }
        }

        Record toInsert;

        if (chainEnd.getKey() < key) {
            // updating pointer at the end of overflow chain
            chainEnd.setPointer(overflowCt);
            if (chainEnd == page[overflowEntry]) {
                savePage(page, pageNo, main);
            } else {
                savePage(chainEndPage, prevChain.getPointer()/PAGE_MAIN, overflow);
            }
            toInsert = new Record(key, val, -1);
        }
        else {
            toInsert = new Record(key, val, prevChain.getPointer());
            prevChain.setPointer(overflowCt);
            if (prevChain == page[overflowEntry]) {
                savePage(page, pageNo, main);
            }
            else {
                savePage(prevChainPage, prevPointer/PAGE_MAIN, overflow);
            }
        }

        // writing record to end of overflow zone
        appendOverflow(toInsert);


        return true;
    }

    private int getPage(int searched_key) {
        int p = 0;
        int k = (int)Math.ceil((double) indexCt / PAGE_INDEX);
        int m = (p+k)/2;
        boolean finished = false;
        Index[] index_page = new Index[PAGE_INDEX];
        Index next_index = null;
        int found_position = -1;
        while (!finished) {
            // tworzenie bufora strony indeksów
            byte[] index_buf = new byte[(PAGE_INDEX+1)*INDEX_ENT_SIZE];
            try {
                // ładowanie odpowiedniej strony indeksów do pamięci głównej
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
                // jak poniżej pierwszego indeksu
                if (k == -1) return -1;
                continue;
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

    private void savePage(Record[] page, int pageNo, RandomAccessFile file) {
        byte[] buffer = new byte[PAGE_MAIN*DATA_ENT_SIZE];
        for (int i=0; i<page.length; ++i) {
            byte[] buf_record = new byte[DATA_ENT_SIZE];
            if (page[i] != null) buf_record = page[i].toByte();
            System.arraycopy(buf_record, 0, buffer, i*DATA_ENT_SIZE, DATA_ENT_SIZE);
        }
        try {
            file.seek((long) pageNo * PAGE_MAIN * DATA_ENT_SIZE);
            file.write(buffer);
            saves++;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveIndices(Index[] indices, RandomAccessFile file) {
        byte[] buffer = new byte[PAGE_INDEX*INDEX_ENT_SIZE];
        int indicesToAdd = 0;
        for (int i=0; i<indices.length; ++i) {
            byte[] buf_record = new byte[INDEX_ENT_SIZE];
            if (indices[i] != null) {
                buf_record = indices[i].toByte();
                indicesToAdd++;
            }
            System.arraycopy(buf_record, 0, buffer, i*INDEX_ENT_SIZE, INDEX_ENT_SIZE);
        }

        try {
            file.seek((long) indexCt * INDEX_ENT_SIZE);
            file.write(buffer);
            saves++;
            indexCt += indicesToAdd;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void appendOverflow(Record record) {
        Record[] page = readPage(overflowCt/PAGE_MAIN, overflow);
        page[overflowCt - ((overflowCt/PAGE_MAIN)*PAGE_MAIN)] = record;
        savePage(page, overflowCt/PAGE_MAIN, overflow);
        overflowCt++;
    }

    public Record[] readPage(int pageNo, RandomAccessFile file) {
        byte[] page_buffer = new byte[PAGE_MAIN*DATA_ENT_SIZE];
        Record[] page = new Record[PAGE_MAIN];
        try {
            file.seek((long) pageNo *PAGE_MAIN*DATA_ENT_SIZE);
            file.read(page_buffer);
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

        return page;
    }


    public void previev() {
        System.out.println("Podgląd wszystkich rekordów");
        // ściągaj stronę po stronie
        for (int i=0; i<indexCt; ++i) {
            Record[] page = readPage(i, main);
            for (int j=0; j<PAGE_INDEX; ++j) {
                if (page[j] != null) {
                    System.out.println("Key: " + page[j].getKey() + " Val: " + page[j].getText());
                    Record chainEnd = page[j];
                    Record prevChain = page[j];
                    Record[] chainEndPage = new Record[PAGE_MAIN];
                    while (chainEnd.getPointer() != -1) {
                        // czytanie z overflow
                        if (chainEnd == page[j] || (chainEnd.getPointer()/PAGE_MAIN) != prevChain.getPointer()/PAGE_MAIN) {
                            chainEndPage = readPage(chainEnd.getPointer() / PAGE_MAIN, overflow);
                        }
                        prevChain = chainEnd;
                        chainEnd = chainEndPage[chainEnd.getPointer() - (chainEnd.getPointer()/PAGE_MAIN)*PAGE_MAIN];
                        System.out.println("(OF) Key: " + chainEnd.getKey() + " Val: " + chainEnd.getText());
                    }
                }
            }
        }
    }

    public void allFiles() {
        System.out.println("Podgląd wszystkich plików");
        System.out.println("---PLIK INDEKSU---");
        Index[] index_page = new Index[PAGE_INDEX];
        int index_pageCt = (int)Math.ceil((double)indexCt/PAGE_INDEX);
        for (int i=0; i<index_pageCt; ++i) {
            byte[] index_buf = new byte[(PAGE_INDEX + 1) * INDEX_ENT_SIZE];
            try {
                // ładowanie odpowiedniej strony indeksów do pamięci głównej
                index.seek((long) i * PAGE_INDEX * INDEX_ENT_SIZE);
                index.read(index_buf);
                reads++;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            // rozdzielanie bufora indeksów i wpisanie do strony
            for (int j = 0; j < PAGE_INDEX; ++j) {
                // tworzenie bufora na pojedynczy indeks
                byte[] single_index_buf = new byte[INDEX_ENT_SIZE];
                System.arraycopy(index_buf, j * INDEX_ENT_SIZE, single_index_buf, 0, INDEX_ENT_SIZE);
                // parsowanie i wpisanie indeksu na strone
                index_page[j] = Index.byteToIndex(single_index_buf);
            }

            System.out.println("-STRONA INDEKSÓW " + i + " -");
            for (int j = 0; j < PAGE_INDEX; ++j) {
                if (index_page[j] != null) {
                    System.out.println(index_page[j].toString());
                }
            }
        }

        System.out.println("---OBSZAR GŁÓWNY---");
        System.out.println("Klucz  Wartość  Wskaźnik OF");
        for (int i=0; i<indexCt; ++i) {
            Record[] page = readPage(i, main);
            System.out.println("-STRONA "+i+"-");
            for (int j=0; j<PAGE_INDEX; ++j) {
                if (page[j] != null) {
                    System.out.println(page[j].toString());
                }
            }
        }

        System.out.println("---OBSZAR OVERFLOW---");
        System.out.println("Klucz  Wartość  Wskaźnik OF");
        int overflowPages = (int)Math.ceil((double) overflowCt / PAGE_MAIN);
        for (int i=0; i<overflowPages; ++i) {
            Record[] page = readPage(i, overflow);
            System.out.println("-STRONA "+i+"-");
            for (int j=0; j<PAGE_INDEX; ++j) {
                if (page[j] != null) {
                    System.out.println(page[j].toString());
                }
            }
        }
    }

    public void reorganise() {
        int pages = indexCt;
        indexCt = 0;
        recordCt = 0;
        RandomAccessFile newIndexFile, newDataFile;
        try {
            newIndexFile = new RandomAccessFile("index2.txt", "rw");
            newDataFile = new RandomAccessFile("data2.txt", "rw");
        }
        catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Record[] newPage = new Record[PAGE_MAIN];
        int newPageSize = 0;
        int newPageNo = 0;
        Index[] newIndexPage = new Index[PAGE_INDEX];
        int newIndexPageSize = 0;

        Record[] overflowPage = new Record[PAGE_MAIN];
        int currentOverflowPos = -1;

        for (int i=0; i<pages; ++i) {
            Record[] page = readPage(i, main);
            for (int j=0; j<PAGE_MAIN; ++j) {
                if (page[j]==null) continue;
                newPage[newPageSize] = page[j];
                newPageSize++;
                recordCt++;

                Record chainEnd = page[j];
                int chainEndPtr = chainEnd.getPointer();
                page[j].setPointer(-1);
                // dodaj do nowej organizacji
                // TODO wyłącz do osobnej funkcji
                if (newPageSize >= ALPHA * PAGE_MAIN) {
                    savePage(newPage, newPageNo, newDataFile);
                    Index newIndex = new Index(newPage[0].getKey(), newPageNo);
                    newIndexPage[newIndexPageSize] = newIndex;
                    newIndexPageSize++;
                    if (newIndexPageSize >= PAGE_INDEX) {
                        //  zapisz indeksy do pliku
                        saveIndices(newIndexPage, newIndexFile);
                        // czyszczenie bufora indeksów
                        for (int k=0; k<PAGE_INDEX; ++k) newIndexPage[k] = null;
                        newIndexPageSize = 0;
                    }

                    // czyszczenie bufora rekordów
                    for (int k=0; k<PAGE_MAIN; ++k) newPage[k] = null;
                    newPageSize = 0;
                    newPageNo++;
                }

                while (chainEndPtr != -1) {
                    // czytanie z overflow
                    if (currentOverflowPos == -1 || chainEndPtr/PAGE_MAIN != currentOverflowPos/PAGE_MAIN) {
                        overflowPage = readPage(chainEndPtr/PAGE_MAIN, overflow);
                    }
                    currentOverflowPos = chainEndPtr;
                    chainEnd = overflowPage[chainEndPtr - (chainEndPtr/PAGE_MAIN) * PAGE_MAIN];
                    chainEndPtr = chainEnd.getPointer();
                    chainEnd.setPointer(-1);
                    newPage[newPageSize] = chainEnd;
                    newPageSize++;
                    recordCt++;

                    // dodaj do nowej organizacji
                    // TODO wyłącz do osobnej funkcji
                    if (newPageSize >= ALPHA * PAGE_MAIN) {
                        savePage(newPage, newPageNo, newDataFile);
                        Index newIndex = new Index(newPage[0].getKey(), newPageNo);
                        newIndexPage[newIndexPageSize] = newIndex;
                        newIndexPageSize++;
                        if (newIndexPageSize >= PAGE_INDEX) {
                            //  zapisz indeksy do pliku
                            saveIndices(newIndexPage, newIndexFile);
                            // czyszczenie bufora indeksów
                            for (int k=0; k<PAGE_INDEX; ++k) newIndexPage[k] = null;
                            newIndexPageSize = 0;
                        }

                        // czyszczenie bufora rekordów
                        for (int k=0; k<PAGE_MAIN; ++k) newPage[k] = null;
                        newPageSize = 0;
                        newPageNo++;
                    }
                }

            }

        }

        if (newPage[0] != null) {
            // dodaj do nowej organizacji resztki
            // TODO wyłącz do ostatniej funkcji
            savePage(newPage, newPageNo, newDataFile);
            Index newIndex = new Index(newPage[0].getKey(), newPageNo);
            newIndexPage[newIndexPageSize] = newIndex;
            newIndexPageSize++;
        }
        if (newIndexPageSize != 0) {
            //  zapisz indeksy do pliku
            saveIndices(newIndexPage, newIndexFile);
            // czyszczenie bufora indeksów
            for (int k = 0; k < PAGE_INDEX; ++k) newIndexPage[k] = null;

            // czyszczenie bufora rekordów
            for (int k = 0; k < PAGE_MAIN; ++k) newPage[k] = null;

        }

        // usuń stare pliki i przypisz nowe
        Path oldIndexPath = Paths.get("index.txt");
        Path newIndexPath = Paths.get("index2.txt");
        Path oldDataPath = Paths.get("data.txt");
        Path newDataPath = Paths.get("data2.txt");
        try {
            Files.move(newIndexPath, oldIndexPath, REPLACE_EXISTING);
            Files.move(newDataPath, oldDataPath, REPLACE_EXISTING);
            index = newIndexFile;
            main = newDataFile;

            overflow.setLength(0);
            overflowCt = 0;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
