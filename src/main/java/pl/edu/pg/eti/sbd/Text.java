package pl.edu.pg.eti.sbd;

import java.util.Random;

//Delivering text-based useful methods
public class Text {

    public static final int MAX_SIZE = 30;

    // Method to check how many repeating letters does a word have
    public static int checkRepetitions(String text) {
        if (text == null) return -1;
        // main repetition counter
        int reps = 0;

        for (int i=0; i<text.length(); ++i) {
            // counter of earlier reps of currently indexed character
            int repCt = 0;

            for (int j=i-1; j>=0; --j) {
                // The comparator is not case-sensitive, as it should be
                if (Character.toLowerCase(text.charAt(i)) == Character.toLowerCase(text.charAt(j))) {
                    repCt++;
                    // if there is more than one repetition we can end searching (already incremented the main ct)
                    if (repCt>1) break;
                }
            }
            // only incrementing the main counter on the first repetition of the character
            if (repCt==1) reps++;
        }

        return reps;
    }

    // Method that generates a random string with a newline at the end
    public static String generate() {
        // generating size of a string (not zero)
        Random rnd = new Random();
        int size = rnd.nextInt(MAX_SIZE)+1;

        // generating a string letter by letter (only lowercase)
        char[] str = new char[size];
        for (int i=0; i<size; ++i) {
            char a = (char)(rnd.nextInt('z'-'a') +'a');
            str[i] = a;
        }

        // casting a char array to string to return
        return new String(str);
    }

    //Method that checks whether a string is a correct record
    public static boolean checkRecord(String text) {
        // checking for appropriate length
        if (text == null || text.length() == 0 || text.length() > MAX_SIZE) {
            return false;
        }
        for (int i=0; i<text.length(); ++i) {
            // simplifying notation by getting a char at current position to another var
            char position = text.charAt(i);
            // checking if string contains chars other than letters
            if (position < 'A' || (position > 'Z' && position < 'a') || position > 'z') {
                return false;
            }
        }
        return true;
    }
}
