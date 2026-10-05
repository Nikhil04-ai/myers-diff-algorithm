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
 *
 * Terms used below (edit graph of a and b):
 *   x       position in a, y position in b
 *   right   move (x, y) -> (x + 1, y): delete a[x]
 *   down    move (x, y) -> (x, y + 1): insert b[y]
 *   snake   a run of diagonal moves over equal items, which costs nothing
 *   k       diagonal number, k = x - y
 *   D       the number of edits (right and down moves) in a shortest path
 */
public class MyersDiff {

    private final int[] a;
    private final int[] b;
    private final boolean[] deleted;
    private final boolean[] inserted;

    // V arrays. vf[offset + k] is the furthest x reached on diagonal k (k = x - y)
    // going forward from the top-left corner. vb is the same going backward
    // from the bottom-right corner (x and y are then counted from the end).
    // offset shifts k, which can be negative, to a valid array index.
    private final int[] vf;
    private final int[] vb;
    private final int offset;

    private MyersDiff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        this.a = a;
        this.b = b;
        this.deleted = deleted;
        this.inserted = inserted;
        // Each search needs at most (n + m + 1) / 2 rounds, and round d uses
        // diagonals -d - 1 .. d + 1. The arrays are allocated once and reused
        // by every recursive call, since a sub-problem is never larger.
        int max = (a.length + b.length + 1) / 2;
        offset = max + 1;
        vf = new int[2 * max + 3];
        vb = new int[2 * max + 3];
    }

    /** Computes a minimal diff of a and b and marks the result in deleted and inserted. */
    public static void diff(int[] a, int[] b, boolean[] deleted, boolean[] inserted) {
        MyersDiff d = new MyersDiff(a, b, deleted, inserted);
        d.compare(0, a.length, 0, b.length);
    }

    /** Finds a minimal diff of a[aLo..aHi) and b[bLo..bHi) (divide and conquer). */
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

        // Base cases: if one side is empty, everything left on the other side is an edit.
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
        // so the recursion always ends. The snake itself is kept.
        int[] snake = middleSnake(aLo, aHi, bLo, bHi);
        compare(aLo, snake[0], bLo, snake[1]);
        compare(snake[2], aHi, snake[3], bHi);
    }

    /**
     * Runs the greedy search from both ends at the same time until the two
     * searches overlap. The snake where they meet lies on a shortest path.
     *
     * @return {startX, startY, endX, endY} of the middle snake, in absolute positions
     */
    private int[] middleSnake(int aLo, int aHi, int bLo, int bHi) {
        int n = aHi - aLo;
        int m = bHi - bLo;
        // delta is the diagonal of the end point (n, m). If delta is odd, D is odd
        // and the two searches can only meet right after a forward step. If delta
        // is even, D is even and they meet right after a backward step.
        int delta = n - m;
        boolean odd = (delta % 2 != 0);
        int max = (n + m + 1) / 2;

        // Seed so that round d = 0 starts at x = 0 on diagonal 0 (a virtual
        // "down" move from diagonal 1).
        vf[offset + 1] = 0;
        vb[offset + 1] = 0;

        for (int d = 0; d <= max; d++) {

            // Forward search: furthest reaching d-paths from (0, 0).
            for (int k = -d; k <= d; k += 2) {
                // Choose the better neighbour: come down from diagonal k + 1 if it
                // reached further, otherwise go right from diagonal k - 1.
                // At the edges (k = -d or k = d) only one neighbour exists.
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

                // Backward diagonal c is the same line as forward diagonal k.
                // The backward search has finished d - 1 rounds, so only diagonals
                // -(d - 1) .. d - 1 hold valid values. The paths overlap when the
                // forward x plus the backward x (counted from the end) reach n.
                int c = delta - k;
                if (odd && c >= -(d - 1) && c <= d - 1 && x + vb[offset + c] >= n) {
                    return new int[] {aLo + startX, bLo + startY, aLo + x, bLo + y};
                }
            }

            // Backward search: furthest reaching d-paths from (n, m).
            // Here x and y are counted from the end of each sequence, so the code
            // is the same as the forward search, reading a and b from the back.
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

                // The forward search has finished d rounds, so diagonals -d .. d are valid.
                int k = delta - c;
                if (!odd && k >= -d && k <= d && x + vf[offset + k] >= n) {
                    // Convert the backward snake to forward positions: it runs
                    // from (n - x, m - y) to (n - startX, m - startY).
                    return new int[] {aHi - x, bHi - y, aHi - startX, bHi - startY};
                }
            }
        }
        // Not reachable: the searches always meet by round max.
        throw new IllegalStateException("middle snake not found");
    }
}
