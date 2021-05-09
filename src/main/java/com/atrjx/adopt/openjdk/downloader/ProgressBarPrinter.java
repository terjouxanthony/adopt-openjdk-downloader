package com.atrjx.adopt.openjdk.downloader;

public class ProgressBarPrinter {
    private long previousPercentage = -1;
    private long count = 0;
    private final long totalNbElements;
    private final String prefix;

    public ProgressBarPrinter(long totalNbElements, String prefix) {
        this.totalNbElements = totalNbElements;
        this.prefix = prefix;
    }

    public void update(long increment) {
        count += increment;
        final long percentage = (count * 100) / totalNbElements;
        if (previousPercentage != percentage) {
            previousPercentage = percentage;
            printPercentage(percentage);
        }
    }

    private void printPercentage(long percentage) {
        final StringBuilder sb = new StringBuilder();
        sb.append(prefix);
        sb.append(" - ");
        final long nbPercentSymbols = percentage / 4;
        for (long i = 0; i < nbPercentSymbols; i++) {
            sb.append("#");
        }
        sb.append(" [");
        sb.append(percentage);
        sb.append(" %]\r");

        System.out.print(sb);
        if (percentage == 100) {
            System.out.println();
        }
    }
}
