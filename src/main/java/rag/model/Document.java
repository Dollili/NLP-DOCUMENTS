package rag.model;

import java.io.Serial;
import java.io.Serializable;

import static rag.config.AppConfig.DOC_PATH;

public class Document implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public String fileName;
    public String path;
    public float score;

    public Document(String fileName, String path) {
        this.fileName = fileName;
        this.path = path;
        this.score = 0f;
    }

    public void printDocument() {
        System.out.println("::: path: " + DOC_PATH + path + " ::: 유사도: " + score);
    }

}
