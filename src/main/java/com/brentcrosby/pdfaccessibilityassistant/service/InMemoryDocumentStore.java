package com.brentcrosby.pdfaccessibilityassistant.service;

import com.brentcrosby.pdfaccessibilityassistant.domain.StoredDocument;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryDocumentStore {
    private final Map<UUID, StoredDocument> documents = new ConcurrentHashMap<>();

    public StoredDocument save(StoredDocument document) {
        documents.put(document.id(), document);
        return document;
    }

    public StoredDocument get(UUID id) {
        StoredDocument document = documents.get(id);
        if (document == null) {
            throw new DocumentNotFoundException();
        }
        return document;
    }
}
