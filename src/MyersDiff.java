/**
 * Myers' O(ND) diff algorithm, linear-space version (the "middle snake" idea
 * from section 4 of Myers' 1986 paper).
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

    private final int[] a;
    private final int[] b;
    private final boolean[] deleted;
    private final boolean[] inserted;

    // V arrays. vf[offset + k] is the furthest x reached on diagonal k (k = x - y)
    // going forward from the top-left corner. vb is the same going backward
    // from the bottom-right corner (x and y are then counted from the end).
    private final int[] vf;
    private final int[] vb;
    private final int offset;

    private MyersDiff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        this.a = a;
        this.b = b;
        this.deleted = deleted;
        this.inserted = inserted;
        int max = (a.length + b.length + 1) / 2;
        offset = max + 1;
        vf = new int[2 * max + 3];
        vb = new int[2 * max + 3];
    }

    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        MyersDiff d = new MyersDiff(a, b, deleted, inserted);
        d.compare(0, a.length, 0, b.length);
    }

    // Finds a minimal diff of a[aLo..aHi) and b[bLo..bHi).
    private void compare(int aLo, int aHi, int bLo, int bHi) {
        // Skip the common start and the common end, they are always kept.
        while (aLo < aHi && bLo < bHi && a[aLo] == b[bLo]) {
            aLo++;
            bLo++;
        }
        while (aLo < aHi && bLo < bHi && a[aHi - 1] == b[bHi - 1]) {
            aHi--;
            bHi--;
        }

        // If one side is empty, everything left on the other side is an edit.
        if (aLo == aHi) {
            for (int j = bLo; j < bHi; j++) inserted[j] = true;
            return;
        }
        if (bLo == bHi) {
            for (int i = aLo; i < aHi; i++) deleted[i] = true;
            return;
        }

        // Otherwise find the middle snake of an optimal path and solve the
        // part before it and the part after it. Each part needs fewer edits,
        // so the recursion always ends.
        int[] snake = middleSnake(aLo, aHi, bLo, bHi);
        compare(aLo, snake[0], bLo, snake[1]);
        compare(snake[2], aHi, snake[3], bHi);
    }

    // Runs the greedy search from both ends at the same time until the two
    // searches overlap. Returns the snake where they meet as
    // {startX, startY, endX, endY} in absolute positions.
    private int[] middleSnake(int aLo, int aHi, int bLo, int bHi) {
        int n = aHi - aLo;
        int m = bHi - bLo;
        int delta = n - m;
        boolean odd = (delta % 2 != 0);
        int max = (n + m + 1) / 2;

        vf[offset + 1] = 0;
        vb[offset + 1] = 0;

        for (int d = 0; d <= max; d++) {

            // Forward search: furthest reaching d-paths from (0, 0).
            for (int k = -d; k <= d; k += 2) {
                int x;
                if (k == -d || (k != d && vf[offset + k - 1] < vf[offset + k + 1])) {
                    x = vf[offset + k + 1];          // move down (insert)
                } else {
                    x = vf[offset + k - 1] + 1;      // move right (delete)
                }
                int y = x - k;
                int startX = x;
                int startY = y;
                // Follow the snake (diagonal of equal items).
                while (x < n && y < m && a[aLo + x] == b[bLo + y]) {
                    x++;
                    y++;
                }
                vf[offset + k] = x;

                // Backward diagonal c is the same as forward diagonal k.
                int c = delta - k;
                if (odd && c >= -(d - 1) && c <= d - 1 && x + vb[offset + c] >= n) {
                    return new int[] {aLo + startX, bLo + startY, aLo + x, bLo + y};
                }
            }

            // Backward search: furthest reaching d-paths from (n, m).
            // Here x and y are counted from the end of each sequence.
            for (int c = -d; c <= d; c += 2) {
                int x;
                if (c == -d || (c != d && vb[offset + c - 1] < vb[offset + c + 1])) {
                    x = vb[offset + c + 1];
                } else {
                    x = vb[offset + c - 1] + 1;
                }
                int y = x - c;
                int startX = x;
                int startY = y;
                while (x < n && y < m && a[aHi - 1 - x] == b[bHi - 1 - y]) {
                    x++;
                    y++;
                }
                vb[offset + c] = x;

                int k = delta - c;
                if (!odd && k >= -d && k <= d && x + vf[offset + k] >= n) {
                    return new int[] {aHi - x, bHi - y, aHi - startX, bHi - startY};
                }
            }
        }
        throw new IllegalStateException("middle snake not found");
    }
}
