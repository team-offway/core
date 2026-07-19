package com.offway.core.inventory.controller;

import com.offway.core.inventory.service.InventoryService;
import com.offway.core.inventory.service.dto.InventoryRow;
import com.offway.core.inventory.service.dto.InventorySnapshot;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** 지금 가져올 수 있는 데이터 현황 — 정보공유용 표(HTML SSR). */
@Controller
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping(value = "/inventory", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @ResponseBody
    public String inventory() {
        return render(inventoryService.snapshot());
    }

    private String render(InventorySnapshot snap) {
        Map<String, Long> byLabel = snap.rows().stream()
                .collect(Collectors.groupingBy(InventoryRow::statusLabel, Collectors.counting()));
        String summary = byLabel.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(Collectors.joining("&nbsp;·&nbsp;"));

        StringBuilder rows = new StringBuilder();
        for (InventoryRow r : snap.rows()) {
            String note = r.note() == null || r.note().isBlank()
                    ? ""
                    : "<div class=\"note\">" + esc(r.note()) + "</div>";
            rows.append("<tr>")
                    .append("<td class=\"name\">").append(esc(r.name())).append("</td>")
                    .append("<td class=\"prov\">").append(esc(r.provider())).append("</td>")
                    .append("<td><span class=\"pill ").append(r.tone()).append("\">")
                    .append(esc(r.statusLabel())).append("</span>").append(note).append("</td>")
                    .append("<td class=\"prov\">").append(esc(r.provides())).append("</td>")
                    .append("</tr>");
        }

        return """
                <!doctype html><html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>OffWay · 데이터 현황</title>
                <style>
                :root{--ink:#0f1720;--sub:#6b7480;--line:#eceef1;--bg:#fbfbfc}
                *{box-sizing:border-box}
                body{margin:0;background:var(--bg);color:var(--ink);
                  font-family:-apple-system,'Apple SD Gothic Neo',system-ui,Segoe UI,Roboto,sans-serif;
                  line-height:1.55;-webkit-font-smoothing:antialiased}
                .wrap{max-width:860px;margin:0 auto;padding:3.2rem 1.4rem 5rem}
                .kicker{font-size:.8rem;font-weight:700;letter-spacing:.12em;color:#2f6bff;text-transform:uppercase}
                h1{font-size:1.6rem;margin:.35rem 0 .3rem;letter-spacing:-.01em}
                .desc{color:var(--sub);font-size:.95rem;margin:0}
                .summary{margin:1.4rem 0 .2rem;font-size:.85rem;color:var(--sub)}
                table{width:100%%;border-collapse:collapse;margin-top:1rem}
                thead th{text-align:left;font-size:.74rem;font-weight:600;letter-spacing:.04em;
                  color:#9aa2ad;text-transform:uppercase;padding:0 .5rem .7rem;border-bottom:1px solid var(--line)}
                tbody td{padding:1rem .5rem;border-bottom:1px solid var(--line);vertical-align:top;font-size:.92rem}
                td.name{font-weight:650}
                td.prov{color:var(--sub)}
                .note{color:#9aa2ad;font-size:.78rem;margin-top:.3rem}
                .pill{display:inline-block;font-size:.78rem;font-weight:650;padding:.22rem .6rem;border-radius:999px;white-space:nowrap}
                .pill.ok{background:#e7f7ee;color:#12864a}
                .pill.info{background:#e9f0ff;color:#2f5fe0}
                .pill.warn{background:#fdf1dd;color:#b5730a}
                .pill.bad{background:#fdeaea;color:#cf3838}
                .pill.muted{background:#f0f1f3;color:#7c848f}
                footer{margin-top:2.2rem;color:#9aa2ad;font-size:.78rem}
                </style></head><body><div class="wrap">
                <div class="kicker">OffWay · Data Inventory</div>
                <h1>지금 가져올 수 있는 데이터</h1>
                <p class="desc">발급 키로 조회되는 외부 공공데이터와 보유 중인 마스터 데이터 현황입니다.</p>
                <div class="summary">%s</div>
                <table>
                  <thead><tr><th>데이터</th><th>제공처</th><th>상태</th><th>내용</th></tr></thead>
                  <tbody>%s</tbody>
                </table>
                <footer>조회 성공 = 실제 응답 확인 · 승인 반영 대기 = 활용신청 승인이 게이트웨이에 반영되는 중 · 승인·연동 예정 = 키는 승인, 클라이언트 연동 예정</footer>
                </div></body></html>
                """.formatted(summary, rows.toString());
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
