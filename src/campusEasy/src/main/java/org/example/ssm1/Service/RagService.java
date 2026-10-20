package org.example.ssm1.Service;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RagService {

    private final VectorStore vectorStore;
    
    @Value("classpath:files/prompt.txt")
    private Resource promptResource;

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestKnowledgeBase() {
        TextReader textReader = new TextReader(promptResource);
        List<Document> documents = textReader.read();

        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> splitDocuments = splitter.apply(documents);

        vectorStore.add(splitDocuments);
    }

    public List<Document> retrieve(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(5).build());
    }
}
