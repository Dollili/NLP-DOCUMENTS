package rag.config;

public class DataType {
    String INDEX_FILE_JSON = "document_index.json";
    String INDEX_FILE_BINARY = "document_index.dat";

    public String getSaveFormat() {
        return INDEX_FILE_BINARY;
    }
}
