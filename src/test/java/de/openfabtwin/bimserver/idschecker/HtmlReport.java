package de.openfabtwin.bimserver.idschecker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the IDS facet test results as a single self-contained HTML dashboard.
 *
 * <p>Layout: a header with the total file count and overall pass/total, then one collapsible
 * {@code <details>} per facet (the "facet dropdown"). Collapsed, the {@code <summary>} shows the
 * facet name, file count and status; expanded, it reveals a table of
 * {@code file name | target | result | time | status}.
 */
public final class HtmlReport {

    /** One executed test case. {@code ok} = the plugin {@code result} satisfied the {@code target}. */
    public record CaseResult(String facet, String fileName, String target,
                             String result, long timeMs, boolean ok) {}

    private HtmlReport() {}

    public static void write(Path out, List<CaseResult> results) throws IOException {
        // Preserve facet insertion order.
        Map<String, java.util.List<CaseResult>> byFacet = new LinkedHashMap<>();
        for (CaseResult r : results) {
            byFacet.computeIfAbsent(r.facet(), k -> new java.util.ArrayList<>()).add(r);
        }

        int total = results.size();
        long totalOk = results.stream().filter(CaseResult::ok).count();
        long totalMismatch = total - totalOk;
        String generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n");
        sb.append("<title>IDS facet test report</title>\n");
        sb.append(style());
        sb.append("</head>\n<body>\n");

        sb.append("<h1>IDS Facet Test Report</h1>\n");
        sb.append("<p class=\"meta\">Generated ").append(esc(generated)).append("</p>\n");
        sb.append("<div class=\"summary\">\n");
        sb.append(stat("Total test files", String.valueOf(total)));
        sb.append(stat("Passed", totalOk + " / " + total));
        sb.append(stat("Mismatches", String.valueOf(totalMismatch)));
        sb.append("<span class=\"badge ").append(totalMismatch == 0 ? "ok" : "bad").append("\">")
          .append(totalMismatch == 0 ? "ALL OK" : totalMismatch + " MISMATCH").append("</span>\n");
        sb.append("</div>\n");

        for (Map.Entry<String, java.util.List<CaseResult>> e : byFacet.entrySet()) {
            java.util.List<CaseResult> cases = e.getValue();
            long ok = cases.stream().filter(CaseResult::ok).count();
            boolean facetOk = ok == cases.size();

            sb.append("<details>\n<summary>");
            sb.append("<span class=\"facet\">").append(esc(e.getKey())).append("</span>");
            sb.append("<span class=\"count\">").append(cases.size()).append(" files</span>");
            sb.append("<span class=\"count\">").append(ok).append(" / ").append(cases.size()).append(" passed</span>");
            sb.append("<span class=\"badge ").append(facetOk ? "ok" : "bad").append("\">")
              .append(facetOk ? "OK" : (cases.size() - ok) + " MISMATCH").append("</span>");
            sb.append("</summary>\n");

            sb.append("<table>\n<thead><tr>")
              .append("<th>File name</th><th>Target</th><th>Result</th><th>Time (ms)</th><th>Status</th>")
              .append("</tr></thead>\n<tbody>\n");
            for (CaseResult r : cases) {
                sb.append("<tr class=\"").append(r.ok() ? "row-ok" : "row-bad").append("\">");
                sb.append("<td class=\"file\">").append(esc(r.fileName())).append("</td>");
                sb.append("<td>").append(esc(r.target())).append("</td>");
                sb.append("<td>").append(esc(r.result())).append("</td>");
                sb.append("<td class=\"num\">").append(r.timeMs()).append("</td>");
                sb.append("<td><span class=\"badge ").append(r.ok() ? "ok" : "bad").append("\">")
                  .append(r.ok() ? "PASS" : "FAIL").append("</span></td>");
                sb.append("</tr>\n");
            }
            sb.append("</tbody>\n</table>\n</details>\n");
        }

        sb.append("</body>\n</html>\n");
        Files.createDirectories(out.getParent());
        Files.write(out, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String stat(String label, String value) {
        return "<div class=\"stat\"><span class=\"label\">" + esc(label) + "</span>"
                + "<span class=\"value\">" + esc(value) + "</span></div>\n";
    }

    private static String style() {
        return "<style>\n"
                + "body{font-family:system-ui,Segoe UI,Arial,sans-serif;margin:24px;color:#1c2230;background:#fff}\n"
                + "h1{font-size:20px;margin:0 0 4px}\n"
                + ".meta{color:#6b7280;margin:0 0 16px;font-size:13px}\n"
                + ".summary{display:flex;gap:16px;align-items:center;flex-wrap:wrap;"
                + "padding:12px 16px;border:1px solid #e5e7eb;border-radius:8px;margin-bottom:18px;background:#f9fafb}\n"
                + ".stat{display:flex;flex-direction:column}\n"
                + ".stat .label{font-size:11px;text-transform:uppercase;letter-spacing:.04em;color:#6b7280}\n"
                + ".stat .value{font-size:18px;font-weight:600}\n"
                + "details{border:1px solid #e5e7eb;border-radius:8px;margin-bottom:10px;overflow:hidden}\n"
                + "summary{cursor:pointer;list-style:none;display:flex;gap:14px;align-items:center;"
                + "padding:10px 14px;background:#f3f4f6;font-weight:600}\n"
                + "summary::-webkit-details-marker{display:none}\n"
                + "summary::before{content:'\\25B6';font-size:10px;color:#6b7280;transition:transform .15s}\n"
                + "details[open] summary::before{transform:rotate(90deg)}\n"
                + ".facet{text-transform:capitalize;min-width:130px}\n"
                + ".count{font-weight:400;color:#6b7280;font-size:13px}\n"
                + "table{width:100%;border-collapse:collapse;font-size:13px}\n"
                + "th,td{text-align:left;padding:7px 14px;border-top:1px solid #eef0f3}\n"
                + "th{background:#fafbfc;color:#6b7280;font-weight:600;font-size:11px;text-transform:uppercase}\n"
                + "td.file{font-family:ui-monospace,Consolas,monospace;font-size:12px}\n"
                + "td.num{text-align:right;color:#6b7280}\n"
                + ".row-bad{background:#fef2f2}\n"
                + ".badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:700}\n"
                + ".badge.ok{background:#dcfce7;color:#166534}\n"
                + ".badge.bad{background:#fee2e2;color:#991b1b}\n"
                + "</style>\n";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
