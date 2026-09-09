package com.brentcrosby.pdfaccessibilityassistant.api;

import com.brentcrosby.pdfaccessibilityassistant.service.PortfolioBenchmarkService;
import com.brentcrosby.pdfaccessibilityassistant.service.PortfolioSamples;
import com.brentcrosby.pdfaccessibilityassistant.service.PdfInputException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/benchmarks")
public class BenchmarkController {
    private final PortfolioBenchmarkService benchmarks;
    public BenchmarkController(PortfolioBenchmarkService benchmarks){this.benchmarks=benchmarks;}
    @GetMapping("/samples/{number}") public ResponseEntity<byte[]> sample(@PathVariable int number){
        if(number<1 || number>2)throw new PdfInputException("Choose benchmark sample 1 or 2.");
        return ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Type","application/pdf")
                .header("Content-Disposition","attachment; filename=layout-"+number+".pdf").body(PortfolioSamples.untagged(number-1).pdf());
    }
    @PostMapping("/run") public ResponseEntity<PortfolioBenchmarkService.Report> run(){return ResponseEntity.ok().header("Cache-Control","no-store").body(benchmarks.run(null));}
    @PostMapping("/compare") public ResponseEntity<PortfolioBenchmarkService.Report> compare(@Valid @RequestBody PortfolioBenchmarkService.Comparison request){return ResponseEntity.ok().header("Cache-Control","no-store").body(benchmarks.run(request));}
}
