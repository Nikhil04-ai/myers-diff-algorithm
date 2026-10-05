import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

/**
 * Command-line entry point for the Myers diff assignment.
 *
 * Usage:
 *   java Main lines A B       line diff of file A to file B (Part A)
 *   java Main highlight A B   the same diff plus changed-character ranges (Part B)
 *
 * Output format, one line per entry of the edit script:
 *   ' ' + line   kept (the line is in both files)
 *   '-' + line   deleted (the line is only in A)
 *   '+' + line   inserted (the line is only in B)
 *   "? old | new" (highlight only) after each paired '+' line
 *
 * Exit codes: 0 on success, 2 for bad arguments or an unreadable file.
 */
public class Main {

    public static void main(String[] args) throws IOException {
        // Validate the command line before doing any work.
        if (args.length != 3 || !(args[0].equals("lines") || args[0].equals("highlight"))) {
            System.err.println("usage: java Main lines|highlight A B");
            System.exit(2);
        }
        boolean highlight = args[0].equals("highlight");

        // Read both files before printing anything, so a read error leaves stdout empty.
        byte[] dataA = readFile(args[1]);
        byte[] dataB = readFile(args[2]);

        // Each line is stored as a start and end position in the file's bytes.
        // The end does not include the '\n'. No bytes are copied at this point.
        int[][] linesA = splitLines(dataA);
        int[][] linesB = splitLines(dataB);
        int[] startA = linesA[0], endA = linesA[1];
        int[] startB = linesB[0], endB = linesB[1];
        int n = startA.length;
        int m = startB.length;

        // Give every distinct line a number, so lines are compared as ints.
        // Both files share one map, so equal lines in A and B get the same id.
        HashMap<String, Integer> ids = new HashMap<>(2 * (n + m) + 16);
        int[] a = lineIds(dataA, startA, endA, ids);
        int[] b = lineIds(dataB, startB, endB, ids);

        // Compute the minimal diff: deleted[i] / inserted[j] mark the changed lines.
        boolean[] deleted = new boolean[n];
        boolean[] inserted = new boolean[m];
        diffLines(a, b, ids.size(), deleted, inserted);

        // Print the edit script. i walks through A and j walks through B.
        // A large buffer avoids a system call for every output line.
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1 << 16);
        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            // Both current lines are kept: they are the same line, print it once.
            if (i < n && j < m && !deleted[i] && !inserted[j]) {
                writeLine(out, ' ', dataA, startA[i], endA[i]);
                i++;
                j++;
                continue;
            }

            // A change block: the run of deleted lines in A and the run of
            // inserted lines in B up to the next kept line. Printing all the
            // deletions before the insertions satisfies the delete-first rule.
            int delStart = i;
            int insStart = j;
            while (i < n && deleted[i]) i++;
            while (j < m && inserted[j]) j++;
            for (int p = delStart; p < i; p++) {
                writeLine(out, '-', dataA, startA[p], endA[p]);
            }
            for (int q = insStart; q < j; q++) {
                writeLine(out, '+', dataB, startB[q], endB[q]);
                // The k-th '+' line of the block is paired with the k-th '-' line.
                // Leftover lines on either side stay unpaired and get no '?' line.
                int p = delStart + (q - insStart);
                if (highlight && p < i) {
                    writeHighlight(out, dataA, startA[p], endA[p], dataB, startB[q], endB[q]);
                }
            }
        }
        out.flush();
    }

    /**
     * Reads the whole file as raw bytes (not as text, so "\r\n" is preserved).
     * If the file cannot be read, prints an error on stderr and exits with code 2.
     */
    static byte[] readFile(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (Exception e) {
            System.err.println("error: cannot read file " + path);
            System.exit(2);
            return null;   // never reached, but the compiler needs a return value
        }
    }

    /**
     * Splits the data into lines on the '\n' byte.
     * A final empty piece is dropped (so a trailing newline adds no extra line,
     * and an empty file has no lines). A '\r' stays part of its line.
     *
     * @return {starts, ends}: line i is data[starts[i] .. ends[i])
     */
    static int[][] splitLines(byte[] data) {
        // First pass: count the lines so the arrays can be allocated exactly.
        int count = 0;
        for (byte x : data) {
            if (x == '\n') count++;
        }
        if (data.length > 0 && data[data.length - 1] != '\n') count++;

        // Second pass: record where every line starts and ends.
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

    /**
     * Turns each line into an integer id. Lines with identical bytes get the same id,
     * so comparing two lines later is a single int comparison.
     * ISO_8859_1 maps every byte to exactly one char, so the String key is an exact
     * copy of the bytes, even when the file is not valid UTF-8.
     */
    static int[] lineIds(byte[] data, int[] starts, int[] ends, HashMap<String, Integer> ids) {
        int[] result = new int[starts.length];
        for (int i = 0; i < starts.length; i++) {
            String key = new String(data, starts[i], ends[i] - starts[i], StandardCharsets.ISO_8859_1);
            Integer id = ids.get(key);
            if (id == null) {         // first time this line is seen: give it the next id
                id = ids.size();
                ids.put(key, id);
            }
            result[i] = id;
        }
        return result;
    }

    /**
     * Computes a minimal line diff of a and b.
     *
     * A line of A that never appears in B can never be kept (and the same for B),
     * so it is marked as changed right away and left out of the Myers search.
     * Every minimal diff deletes or inserts such lines anyway, so the result stays
     * minimal, and the search gets much smaller when many lines changed.
     */
    static void diffLines(int[] a, int[] b, int idCount, boolean[] deleted, boolean[] inserted) {
        // How many times each line id occurs in each file.
        int[] countA = new int[idCount];
        int[] countB = new int[idCount];
        for (int id : a) countA[id]++;
        for (int id : b) countB[id]++;

        // Positions of the lines that occur in both files.
        int[] posA = commonPositions(a, countB, deleted);
        int[] posB = commonPositions(b, countA, inserted);

        // Build the shorter sequences that only contain those lines.
        int[] shortA = new int[posA.length];
        int[] shortB = new int[posB.length];
        for (int t = 0; t < posA.length; t++) shortA[t] = a[posA[t]];
        for (int t = 0; t < posB.length; t++) shortB[t] = b[posB[t]];

        // Run Myers on the shorter sequences.
        boolean[] shortDeleted = new boolean[posA.length];
        boolean[] shortInserted = new boolean[posB.length];
        MyersDiff.diff(shortA, shortB, shortDeleted, shortInserted);

        // Copy the result back to the original line positions.
        for (int t = 0; t < posA.length; t++) deleted[posA[t]] = shortDeleted[t];
        for (int t = 0; t < posB.length; t++) inserted[posB[t]] = shortInserted[t];
    }

    /**
     * Returns the positions of the lines in seq that also occur in the other file,
     * and marks every other line as changed.
     */
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

    /** Writes one output line: the prefix character, the line's original bytes, then '\n'. */
    static void writeLine(BufferedOutputStream out, char prefix, byte[] data, int start, int end) throws IOException {
        out.write(prefix);
        out.write(data, start, end - start);
        out.write('\n');
    }

    /**
     * Part B: diffs the characters of a paired '-' and '+' line and prints
     * "? <old ranges> | <new ranges>".
     * Lines are decoded as UTF-8 and split into code points, so an emoji counts
     * as one character (a Java char would count it as two).
     */
    static void writeHighlight(BufferedOutputStream out, byte[] dataA, int startA, int endA,
                               byte[] dataB, int startB, int endB) throws IOException {
        int[] oldChars = new String(dataA, startA, endA - startA, StandardCharsets.UTF_8).codePoints().toArray();
        int[] newChars = new String(dataB, startB, endB - startB, StandardCharsets.UTF_8).codePoints().toArray();

        // Same Myers diff as for lines, now on characters: deleted chars are the
        // changed ones in the old line, inserted chars the changed ones in the new line.
        boolean[] deleted = new boolean[oldChars.length];
        boolean[] inserted = new boolean[newChars.length];
        MyersDiff.diff(oldChars, newChars, deleted, inserted);

        String line = "? " + ranges(deleted) + " | " + ranges(inserted) + "\n";
        out.write(line.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Turns the marked positions into comma-separated "start-end" ranges
     * (end not included), or "." when nothing is marked.
     * Each range covers a whole run of consecutive marked positions, so the
     * ranges come out in order, never overlap, and touching ranges are merged.
     * Example: marked positions 3, 4, 9 give "3-5,9-10".
     */
    static String ranges(boolean[] changed) {
        StringBuilder sb = new StringBuilder();
        int p = 0;
        while (p < changed.length) {
            if (!changed[p]) {
                p++;
                continue;
            }
            int start = p;                                 // first position of the run
            while (p < changed.length && changed[p]) p++;  // p ends just after the run
            if (sb.length() > 0) sb.append(',');
            sb.append(start).append('-').append(p);
        }
        return sb.length() == 0 ? "." : sb.toString();
    }
}
