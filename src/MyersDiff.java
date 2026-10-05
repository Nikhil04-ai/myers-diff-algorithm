import java.util.ArrayList;
import java.util.Arrays;

/**
 * Myers' O(ND) diff algorithm (Myers 1986, the basic greedy version).
 *
 * It works on any two sequences of ints, so the same code is used for
 * lines (Part A, each line is turned into an id) and for characters
 * (Part B, each character is a Unicode code point).
 *
 * The result is written into two boolean arrays:
 *   deleted[i]  is true when a[i] is deleted (only in A)
 *   inserted[j] is true when b[j] is inserted (only in B)
 * Everything not marked is kept. The number of marked items is the minimum.
 */
public class MyersDiff {

    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        // Step 1: skip the common start and the common end. They are always kept,
        // and this makes the search below much smaller.
        int start = 0;
        while (start < a.length && start < b.length && a[start] == b[start]) {
            start++;
        }
        int endA = a.length;
        int endB = b.length;
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
            endA--;
            endB--;
        }
        int n = endA - start;   // length of the middle part of a
        int m = endB - start;   // length of the middle part of b

        // Step 2: the forward search.
        // v[offset + k] = furthest x reached so far on diagonal k (k = x - y).
        // After each round d we save a copy of v[-d..d] in trace, so we can
        // walk the path back afterwards.
        int max = n + m;
        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        ArrayList<int[]> trace = new ArrayList<>();
        int finalD = -1;

        for (int d = 0; d <= max && finalD < 0; d++) {
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) {
                    x = v[offset + k + 1];        // move down from diagonal k+1 (insert)
                } else {
                    x = v[offset + k - 1] + 1;    // move right from diagonal k-1 (delete)
                }
                int y = x - k;
                // Follow the snake: equal items cost nothing.
                while (x < n && y < m && a[start + x] == b[start + y]) {
                    x++;
                    y++;
                }
                v[offset + k] = x;
                if (x >= n && y >= m) {           // reached the end (n, m)
                    finalD = d;
                    break;
                }
            }
            if (finalD < 0) {
                trace.add(Arrays.copyOfRange(v, offset - d, offset + d + 1));
            }
        }

        // Step 3: backtrack from (n, m) to (0, 0) using the saved V arrays.
        // trace.get(d) holds diagonals -d..d, diagonal k is at index k + d.
        int x = n;
        int y = m;
        for (int d = finalD; d > 0; d--) {
            int[] prev = trace.get(d - 1);
            int k = x - y;
            int prevK;
            if (k == -d || (k != d && prev[k - 1 + (d - 1)] < prev[k + 1 + (d - 1)])) {
                prevK = k + 1;   // we came down from diagonal k+1
            } else {
                prevK = k - 1;   // we came right from diagonal k-1
            }
            int prevX = prev[prevK + (d - 1)];
            int prevY = prevX - prevK;

            if (prevK == k + 1) {
                inserted[start + prevY] = true;   // the down move inserts b[prevY]
            } else {
                deleted[start + prevX] = true;    // the right move deletes a[prevX]
            }
            // The snake between the move and (x, y) is kept, so nothing to mark.
            x = prevX;
            y = prevY;
        }
        // What is left from (0, 0) to (x, y) is the first snake: all kept.
    }
}
