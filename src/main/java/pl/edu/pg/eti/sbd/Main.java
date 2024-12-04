package pl.edu.pg.eti.sbd;

import java.util.Scanner;


public class Main {

    static Scanner scanner;
    static Structure structure;

    public static void AddRecordMenu() {
        System.out.println("Podaj wartość klucza:");
        String in = scanner.nextLine();
        int key = Integer.parseInt(in);
        System.out.println("Podaj rekord:");
        in = scanner.nextLine();
        if (!Text.checkRecord(in)) {
            System.out.println("Zły rekord!");
            return;
        }
        if (!structure.insertRecord(key, in)) {
            System.out.println("Klucz już istnieje!");
        }
        else {
            System.out.println("Dodano rekord!");
        }
    }

    // TODO list orders from file
    public static void OrderListMenu() {
        System.out.println("Podaj ścieżkę do pliku do wczytania rozkazów");
        String path = scanner.nextLine();
    }

    public static boolean MainMenu() {
        System.out.println("Wybierz opcję:");
        System.out.println("A - dodaj rekord");
        System.out.println("D - usuń rekord");
        System.out.println("R - reorganizuj plik");
        System.out.println("P - podgląd pliku");
        System.out.println("L - rozkazy z pliku");
        System.out.println("Q - zamknij program");
        Scanner scanner = new Scanner(System.in);
        char input = 0;
        while (input == 0) {
            String in = scanner.nextLine();
            input = in.charAt(0);
            switch (input) {
                case 'A':
                    // add record
                    AddRecordMenu();
                    break;
                case 'D':
                    // delete record
                    break;
                case 'R':
                    // reorganise
                    break;
                case 'P':
                    // preview
                    break;
                case 'L':
                    OrderListMenu();
                    break;
                case 'Q':
                    System.out.println("Zamykanie programu");
                    return true;
                default:
                    input = 0;
            }
        }
        return false;
    }
    public static void main(String[] args) {
        System.out.println("STRUKTURY BAZ DANYCH 2\nJAKUB SORDYL 193040");
        boolean exit = false;
        scanner = new Scanner(System.in);
        structure = new Structure();
        while (!exit) {
            exit = MainMenu();
        }
        scanner.close();
    }
}