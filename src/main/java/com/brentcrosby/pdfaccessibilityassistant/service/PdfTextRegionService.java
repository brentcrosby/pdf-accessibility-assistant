package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.TextRegion;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Compatibility facade for the first inspector API. New clients inspect one page at a time. */
@Service
public class PdfTextRegionService {
    private final PdfObservationService observations = new PdfObservationService();

    public List<TextRegion> locate(byte[] bytes) {
        var first = observations.inspect(bytes, 1);
        if (first.pageCount() > 100) throw new PdfInputException("Use the page-by-page inspector for PDFs over 100 pages.");
        List<TextRegion> result = new ArrayList<>();
        for (int page = 1; page <= first.pageCount(); page++) {
            var snapshot = page == 1 ? first : observations.inspect(bytes, page);
            snapshot.regions().stream().filter(region -> region.kind().equals("TEXT")).forEach(region -> {
                var b = region.bounds();
                result.add(new TextRegion(region.id(), snapshot.pageNumber(), b.x(), b.y(), b.width(), b.height(), region.text()));
            });
        }
        return List.copyOf(result);
    }

    public byte[] renderPage(byte[] bytes, int pageNumber) {
        return observations.render(bytes, pageNumber);
    }

    public PdfObservationService.PageObservations inspectPage(byte[] bytes, int pageNumber) {
        return observations.inspect(bytes, pageNumber);
    }
}
