package rag.model;

import org.apache.commons.io.FilenameUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

public class Document implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public String fileName;
    public String path;
    public float score;
    public String extension;

    public Document(String fileName, String path) {
        this.fileName = fileName;
        this.path = path;
        this.score = 0f;
        this.extension = FilenameUtils.getExtension(fileName).toLowerCase();
    }

    public void printDocument() {
        System.out.println("::: path: " + path + " ::: 유사도: " + score);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, path);
    }

}
