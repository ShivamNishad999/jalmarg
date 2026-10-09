package org.growforge.jalmarg.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.growforge.jalmarg.dto.Dtos.HotspotView;
import org.growforge.jalmarg.model.Hotspot;
import org.growforge.jalmarg.model.Report;
import org.growforge.jalmarg.repo.HotspotRepository;
import org.growforge.jalmarg.repo.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Builds the evidence PDF a resident or RWA can send to MCD / Noida Authority / GMDA:
 * which spots flood again and again, how deep, and how often, with a request for a
 * permanent drainage fix. Turns one-off complaints into data.
 */
@Service
public class PdfReportService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private static final Color BRAND = new Color(14, 94, 140);
    private static final Font H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, BRAND);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.DARK_GRAY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
    private static final Font TD = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

    private final HotspotService hotspotService;
    private final HotspotRepository hotspots;
    private final ReportRepository reports;

    public PdfReportService(HotspotService hotspotService, HotspotRepository hotspots, ReportRepository reports) {
        this.hotspotService = hotspotService;
        this.hotspots = hotspots;
        this.reports = reports;
    }

    @Transactional(readOnly = true)
    public byte[] build(String area, Long hotspotId) {
        try {
            return buildPdf(area, hotspotId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Could not build PDF: " + e.getMessage(), e);
        }
    }

    private byte[] buildPdf(String area, Long hotspotId) throws Exception {
        List<HotspotView> list;
        if (hotspotId != null) {
            Hotspot h = hotspots.findById(hotspotId).orElseThrow(() -> new IllegalArgumentException("Hotspot not found"));
            list = List.of(hotspotService.view(h));
        } else {
            list = hotspotService.all().stream()
                    .filter(h -> area == null || area.isBlank() || area.equalsIgnoreCase(h.area()))
                    .toList();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        String where = hotspotId != null && !list.isEmpty() ? list.get(0).name()
                : (area == null || area.isBlank() ? "Delhi NCR" : area);
        doc.add(new Paragraph("JalMarg - Waterlogging Evidence Report", H1));
        doc.add(new Paragraph(where + "  |  Generated " + ZonedDateTime.now(IST).format(DT) + " IST", SMALL));
        doc.add(Chunk.NEWLINE);

        String authority = authorityFor(area, list);
        doc.add(new Paragraph("To: " + authority, BODY));
        doc.add(new Paragraph("Subject: Request for a permanent drainage fix at recurring waterlogging spots", H2));
        doc.add(Chunk.NEWLINE);

        int total = list.stream().mapToInt(HotspotView::totalReports).sum();
        doc.add(new Paragraph(String.format(Locale.ROOT,
                "Residents using JalMarg have filed %d waterlogging reports across %d locations listed below. "
                        + "Each report carries a GPS location, a time stamp and a water-depth estimate "
                        + "(from a photo analysed by AI on Amazon Bedrock and/or the reporter). These spots flood "
                        + "repeatedly, put two-wheeler riders and pedestrians at risk, and block traffic. "
                        + "We request an inspection of the storm-water drains at these locations and a "
                        + "time-bound plan for a permanent fix before the next monsoon.",
                total, list.size()), BODY));
        doc.add(Chunk.NEWLINE);

        // Summary table
        PdfPTable t = new PdfPTable(new float[]{0.5f, 3.2f, 1.3f, 1f, 1.1f, 1.2f, 1.6f});
        t.setWidthPercentage(100);
        for (String h : List.of("#", "Location", "Area", "Reports", "Last 30d", "Max depth", "Last flooded")) {
            PdfPCell c = new PdfPCell(new Phrase(h, TH));
            c.setBackgroundColor(BRAND);
            c.setPadding(4);
            t.addCell(c);
        }
        int i = 1;
        for (HotspotView h : list) {
            t.addCell(cell(String.valueOf(i++)));
            t.addCell(cell(h.name() + String.format(Locale.ROOT, "\n(%.4f, %.4f)", h.latitude(), h.longitude())));
            t.addCell(cell(h.area()));
            t.addCell(cell(String.valueOf(h.totalReports())));
            t.addCell(cell(String.valueOf(h.reportsLast30Days())));
            t.addCell(cell(String.format(Locale.ROOT, "%.1f ft", h.maxDepthFt())));
            t.addCell(cell(h.lastSeen() == null ? "-" : h.lastSeen().atZone(IST).format(D)));
        }
        doc.add(t);

        // Detail for the top 5 spots
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("Recent reports at the worst spots", H2));
        for (HotspotView h : list.stream().limit(5).toList()) {
            doc.add(new Paragraph(h.name() + " (" + h.area() + ")", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            List<Report> rs = reports.findByHotspotIdOrderByCreatedAtDesc(h.id()).stream().limit(6).toList();
            for (Report r : rs) {
                doc.add(new Paragraph(String.format(Locale.ROOT, "  - %s: %.1f ft (%s), source %s%s",
                        r.getCreatedAt().atZone(IST).format(DT), r.getDepthFt(), r.getSeverity(),
                        r.getDepthSource(),
                        r.getConfirmations() > 0 ? ", confirmed by " + r.getConfirmations() + " others" : ""), TD));
            }
        }

        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("Depth guide: above 0.5 ft unsafe for two-wheelers, above 0.8 ft unsafe for cars, "
                + "above 1.5 ft unsafe to walk (open drains hidden).", SMALL));
        doc.add(new Paragraph("Generated by JalMarg (Team Growforge). Locations and counts come from citizen "
                + "reports and may include demo data during testing.", SMALL));
        doc.close();
        return out.toByteArray();
    }

    private static PdfPCell cell(String s) {
        PdfPCell c = new PdfPCell(new Phrase(s == null ? "-" : s, TD));
        c.setPadding(4);
        return c;
    }

    /** Who the letter is addressed to. */
    static String authorityFor(String area, List<HotspotView> list) {
        String a = area;
        if ((a == null || a.isBlank()) && list.size() == 1) a = list.get(0).area();
        if (a == null || a.isBlank()) return "Municipal & Development Authorities, Delhi NCR";
        return switch (a.toLowerCase(Locale.ROOT)) {
            case "delhi" -> "Municipal Corporation of Delhi (MCD) / PWD Delhi";
            case "noida" -> "Noida Authority, Gautam Buddha Nagar";
            case "greater noida" -> "Greater Noida Industrial Development Authority (GNIDA)";
            case "gurugram" -> "Gurugram Metropolitan Development Authority (GMDA) / MCG";
            case "ghaziabad" -> "Ghaziabad Municipal Corporation / GDA";
            default -> a + " Municipal Authority";
        };
    }
}
