package rag.model;

public class Tracker {
    private int total;

    public Tracker(int total) {
        this.total = total;
    }

    public void countTotal() {
        this.total++;
    }

    public String getTotal() {
        return String.valueOf(total);
    }
}
