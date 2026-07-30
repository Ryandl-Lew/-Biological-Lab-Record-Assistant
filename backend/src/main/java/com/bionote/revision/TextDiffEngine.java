package com.bionote.revision;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextDiffEngine {
    public Result diff(String before, String after, int maxOperations) {
        String left = before == null ? "" : before, right = after == null ? "" : after;
        if (left.equals(right)) return new Result(List.of(new RevisionDtos.TextOperation("EQUAL", left)), false);
        String[] a = tokens(left), b = tokens(right);
        if ((long) a.length * b.length > 100_000L || a.length > 700 || b.length > 700) {
            return coarse(left, right, maxOperations, true);
        }
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) for (int j = b.length - 1; j >= 0; j--)
            lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
        List<RevisionDtos.TextOperation> operations = new ArrayList<>(); int i = 0, j = 0;
        while (i < a.length || j < b.length) {
            if (i < a.length && j < b.length && a[i].equals(b[j])) { append(operations, "EQUAL", a[i]); i++; j++; }
            else if (j < b.length && (i == a.length || lcs[i][j + 1] >= lcs[i + 1][j])) { append(operations, "INSERT", b[j++]); }
            else { append(operations, "DELETE", a[i++]); }
            if (operations.size() > maxOperations) return new Result(List.copyOf(operations.subList(0, maxOperations)), true);
        }
        return new Result(List.copyOf(operations), false);
    }

    private Result coarse(String before, String after, int maxOperations, boolean truncated) {
        List<RevisionDtos.TextOperation> operations = new ArrayList<>();
        if (!before.isEmpty()) operations.add(new RevisionDtos.TextOperation("DELETE", before));
        if (!after.isEmpty() && operations.size() < maxOperations) operations.add(new RevisionDtos.TextOperation("INSERT", after));
        return new Result(List.copyOf(operations), truncated);
    }
    private String[] tokens(String value) { return value.isEmpty() ? new String[0] : value.split("(?<=\\s)|(?=\\s)"); }
    private void append(List<RevisionDtos.TextOperation> operations, String type, String value) {
        if (!operations.isEmpty() && operations.get(operations.size() - 1).operation().equals(type)) {
            RevisionDtos.TextOperation previous = operations.remove(operations.size() - 1);
            operations.add(new RevisionDtos.TextOperation(type, previous.text() + value));
        } else operations.add(new RevisionDtos.TextOperation(type, value));
    }
    public record Result(List<RevisionDtos.TextOperation> operations, boolean truncated) {}
}
