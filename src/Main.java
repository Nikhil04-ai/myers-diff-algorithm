import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * Usage:
 *   java Main lines A B       line diff of file A to file B (Part A)
 *   java Main highlight A B   the same diff plus changed-character ranges (Part B)
 */
public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: java Main lines|highlight A B");
            System.exit(2);
        }
        boolean highlight = args[0].equals("highlight");

        byte[] dataA = readFile(args[1]);
        byte[] dataB = readFile(args[2]);

        // Each line is stored as a start and end position in the file's bytes.
        // The end does not include the '\n'.
        int[][] linesA = splitLines(dataA);
        int[][] linesB = splitLines(dataB);
        int[] startA = linesA[0], endA = linesA[1];
        int[] startB = linesB[0], endB = linesB[1];
        int n = startA.length;
        int m = startB.length;

        // Give every distinct line a number, so lines are compared as ints.
        HashMap<String, Integer> ids = new HashMap<>(2 * (n + m) + 16);
        int[] a = lineIds(dataA, startA, endA, ids);
        int[] b = lineIds(dataB, startB, endB, ids);

        boolean[] deleted = new boolean[n];
        boolean[] inserted = new boolean[m];
        diffLines(a, b, ids.size(), deleted, inserted);

        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && !deleted[i] && !inserted[j]) {
                writeLine(out, ' ', dataA, startA[i], endA[i]);
                i++;
                j++;
                continue;
            }
            // A change block: all deletions first, then all insertions.
            int delStart = i;
            int insStart = j;
            while (i < n && deleted[i]) i++;
            while (j < m && inserted[j]) j++;
            for (int p = delStart; p < i; p++) {
                writeLine(out, '-', dataA, startA[p], endA[p]);
            }
            for (int q = insStart; q < j; q++) {
                writeLine(out, '+', dataB, startB[q], endB[q]);
                int p = delStart + (q - insStart);   // the paired '-' line
                if (highlight && p < i) {
                    writeHighlight(out, dataA, startA[p], endA[p], dataB, startB[q], endB[q]);
                }
            }
        }
        out.flush();
    }

    // Reads the whole file as raw bytes. On failure: message on stderr, exit code 2.
    static byte[] readFile(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (Exception e) {
            System.err.println("error: cannot read file " + path);
            System.exit(2);
            return null;
        }
    }

    // Splits on '\n'. A final empty piece is dropped, '\r' stays in the line.
    // Returns {starts, ends}.
    static int[][] splitLines(byte[] data) {
        int count = 0;
        for (byte x : data) {
            if (x == '\n') count++;
        }
        if (data.length > 0 && data[data.length - 1] != '\n') count++;

        int[] starts = new int[count];
        int[] ends = new int[count];
        int line = 0;
        int start = 0;
        for (int pos = 0; pos < data.length; pos++) {
            if (data[pos] == '\n') {
                starts[line] = start;
                ends[line] = pos;
                line++;
                start = pos + 1;
            }
        }
        if (line < count) {   // last line without a final '\n'
            starts[line] = start;
            ends[line] = data.length;
        }
        return new int[][] {starts, ends};
    }

    // Turns each line into a number. Equal bytes give the same number.
    // ISO_8859_1 maps every byte to one char, so the String is an exact copy of the bytes.
    static int[] lineIds(byte[] data, int[] starts, int[] ends, HashMap<String, Integer> ids) {
        int[] result = new int[starts.length];
        for (int i = 0; i < starts.length; i++) {
            String key = new String(data, starts[i], ends[i] - starts[i], StandardCharsets.ISO_8859_1);
            Integer id = ids.get(key);
            if (id == null) {
                id = ids.size();
                ids.put(key, id);
            }
            result[i] = id;
        }
        return result;
    }

    // A line of A that never appears in B can never be kept (and the same for B),
    // so it is marked right away and left out of the Myers search. The diff stays
    // minimal, and the search gets much smaller when many lines changed.
    static void diffLines(int[] a, int[] b, int idCount, boolean[] deleted, boolean[] inserted) {
        int[] countA = new int[idCount];
        int[] countB = new int[idCount];
        for (int id : a) countA[id]++;
        for (int id : b) countB[id]++;

        int[] posA = commonPositions(a, countB, deleted);
        int[] posB = commonPositions(b, countA, inserted);

        int[] shortA = new int[posA.length];
        int[] shortB = new int[posB.length];
        for (int t = 0; t < posA.length; t++) shortA[t] = a[posA[t]];
        for (int t = 0; t < posB.length; t++) shortB[t] = b[posB[t]];

        boolean[] shortDeleted = new boolean[posA.length];
        boolean[] shortInserted = new boolean[posB.length];
        MyersDiff.diff(shortA, shortB, shortDeleted, shortInserted);

        for (int t = 0; t < posA.length; t++) deleted[posA[t]] = shortDeleted[t];
        for (int t = 0; t < posB.length; t++) inserted[posB[t]] = shortInserted[t];
    }

    // Returns the positions of lines that also occur in the other file,
    // and marks all other lines as changed.
    static int[] commonPositions(int[] seq, int[] otherCount, boolean[] changed) {
        int size = 0;
        for (int id : seq) {
            if (otherCount[id] > 0) size++;
        }
        int[] positions = new int[size];
        int t = 0;
        for (int i = 0; i < seq.length; i++) {
            if (otherCount[seq[i]] > 0) {
                positions[t++] = i;
            } else {
                changed[i] = true;
            }
        }
        return positions;
    }

    static void writeLine(BufferedOutputStream out, char prefix, byte[] data, int start, int end) throws IOException {
        out.write(prefix);
        out.write(data, start, end - start);
        out.write('\n');
    }

    // Diffs the characters (code points) of a paired '-' and '+' line and
    // prints "? <old ranges> | <new ranges>".
    static void writeHighlight(BufferedOutputStream out, byte[] dataA, int startA, int endA,
                               byte[] dataB, int startB, int endB) throws IOException {
        int[] oldChars = new String(dataA, startA, endA - startA, StandardCharsets.UTF_8).codePoints().toArray();
        int[] newChars = new String(dataB, startB, endB - startB, StandardCharsets.UTF_8).codePoints().toArray();
        boolean[] deleted = new boolean[oldChars.length];
        boolean[] inserted = new boolean[newChars.length];
        MyersDiff.diff(oldChars, newChars, deleted, inserted);

        String line = "? " + ranges(deleted) + " | " + ranges(inserted) + "\n";
        out.write(line.getBytes(StandardCharsets.US_ASCII));
    }

    // Turns marked positions into "start-end" ranges (end not included).
    // Each range is a full run of marked positions, so touching ranges are merged.
    static String ranges(boolean[] changed) {
        StringBuilder sb = new StringBuilder();
        int p = 0;
        while (p < changed.length) {
            if (!changed[p]) {
                p++;
                continue;
            }
            int start = p;
            while (p < changed.length && changed[p]) p++;
            if (sb.length() > 0) sb.append(',');
            sb.append(start).append('-').append(p);
        }
        return sb.length() == 0 ? "." : sb.toString();
    }
}
