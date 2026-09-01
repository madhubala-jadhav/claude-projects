package org.example.report;

import org.example.analysis.CategorySpend;
import org.example.analysis.MonthlySummary;
import org.example.diagnostics.ExcludedFile;
import org.example.diagnostics.RunReport;

/**
 * C8's last line of defence: "Template error → falls back to a minimal, guaranteed-renderable
 * HTML that shows the summary numbers and the diagnostics, so the user always gets something
 * (NFR3)."
 *
 * <p>Deliberately primitive. It uses no template engine, no external asset, no design token and
 * no chart - because every one of those is a thing that could have been what failed. If this
 * page cannot be produced, nothing can.</p>
 */
final class MinimalReport {

    private MinimalReport() {
    }

    static String render(MonthlySummary summary, RunReport runReport) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        sb.append("<title>Expense Summary — ").append(Html.escape(summary.month())).append("</title>");
        sb.append("<style>body{font-family:system-ui,sans-serif;margin:2rem;max-width:52rem}"
                + "table{border-collapse:collapse;width:100%}td,th{border-bottom:1px solid #ccc;"
                + "padding:.4rem;text-align:left}td.n,th.n{text-align:right}</style></head><body>");
        sb.append("<h1>Expense Summary — ").append(Html.escape(summary.month())).append("</h1>");
        sb.append("<p>The full report could not be rendered, so this simplified version was written instead. "
                + "The numbers below are the same ones the full report would have shown.</p>");

        sb.append("<p>Total spend: <strong>").append(summary.totalSpend().toPlainString()).append(' ')
                .append(Html.escape(summary.currency())).append("</strong><br>");
        sb.append("Total income: ").append(summary.totalIncome().toPlainString()).append("<br>");
        sb.append("Net: ").append(summary.net().toPlainString()).append("<br>");
        sb.append("Transactions: ").append(summary.transactionCount()).append("</p>");

        if (summary.topCategory() != null) {
            sb.append("<p>Largest category: <strong>").append(Html.escape(summary.topCategory().name()))
                    .append("</strong> — ").append(summary.topCategory().amount().toPlainString())
                    .append(" (").append(String.format(java.util.Locale.ROOT, "%.1f",
                            summary.topCategory().percentOfSpend())).append("%)</p>");
        }

        sb.append("<table><thead><tr><th>Category</th><th class=\"n\">Txns</th><th class=\"n\">Amount</th>"
                + "<th class=\"n\">%</th></tr></thead><tbody>");
        for (CategorySpend c : summary.byCategory()) {
            sb.append("<tr><td>").append(Html.escape(c.name())).append("</td><td class=\"n\">")
                    .append(c.txnCount()).append("</td><td class=\"n\">").append(c.amount().toPlainString())
                    .append("</td><td class=\"n\">")
                    .append(String.format(java.util.Locale.ROOT, "%.1f", c.percent())).append("</td></tr>");
        }
        sb.append("</tbody></table>");

        if (!runReport.excludedFiles().isEmpty()) {
            sb.append("<h2>Files that were skipped</h2><ul>");
            for (ExcludedFile ex : runReport.excludedFiles()) {
                sb.append("<li>").append(Html.escape(ex.file())).append(" — ")
                        .append(Html.escape(ex.reason())).append("</li>");
            }
            sb.append("</ul>");
        }

        sb.append("<p><a href=\"transactions.csv\">transactions.csv</a> · "
                + "<a href=\"run-log.txt\">run-log.txt</a></p>");
        sb.append("<p><small>Generated locally. No data left this machine.</small></p>");
        sb.append("</body></html>");
        return sb.toString();
    }
}
