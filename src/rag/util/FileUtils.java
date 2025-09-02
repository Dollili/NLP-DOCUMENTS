package rag.util;

import org.apache.commons.io.FilenameUtils;
import rag.model.Document;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

import static rag.config.AppConfig.DOC_PATH;
import static rag.config.AppConfig.DOCUMENTS;

public class FileUtils {
    private static final int MAX_DEPTHS = 4;

    public static class FolderTask extends RecursiveAction {
        private final File folder;
        private final int depth;

        public FolderTask(File folder, int depth) {
            this.folder = folder;
            this.depth = depth;
        }

        @Override
        protected void compute() {
            if (depth > MAX_DEPTHS || !folder.exists() || !folder.canRead()) {
                return;
            }

            File[] files = folder.listFiles();
            if (files == null)
                return;

            List<FolderTask> subTasks = new java.util.ArrayList<>();

            for (File file : files) {
                try {
                    if (file.isFile() && allowed(file)) {
                        if (file.length() < 100 * 1024 * 1024) {
                            DOCUMENTS.add(new Document(file.getName(), file.getAbsolutePath().substring(DOC_PATH.length())));
                        }
                    } else if (file.isDirectory() && !isSystemDirectory(file)) {
                        subTasks.add(new FolderTask(file, depth + 1)); // 하위 디렉토리를 새로운 태스크로 추가
                    }
                } catch (Exception e) {
                    continue;
                }
            }

            // 병렬 실행
            if (!subTasks.isEmpty()) {
                invokeAll(subTasks);
            }
        }
    }

    public static boolean isSystemDirectory(File file) {
        String name = file.getName().toLowerCase();
        return name.startsWith(".") || name.equals("system32") || name.equals("windows") || name.equals("program files") || name.equals("program files (x86)") || name.contains("temp") || name.contains("cache");
    }

    public static boolean allowed(File file) {
        String ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
        Set<String> allowedExts = Set.of(
                // 문서
                "txt", "pdf", "docx", "pptx", "hwp", "xlsx", "csv",
                // 실행 파일
                "exe", "msi", "dmg", "app",
                "html", "xml",
                // 기타
                "md", "rtf", "odt");
        return allowedExts.contains(ext);
    }

    public static String extractMeaningfulText(String fileName, String path) {
        String nameWithoutExt = FilenameUtils.getBaseName(fileName);

        String cleanName = nameWithoutExt.replaceAll("[_-]", " ");

        // 경로에서 마지막 폴더명만 추출
        String[] pathParts = path.split("[/\\\\]");
        String parentFolder = "";
        if (pathParts.length > 1) {
            parentFolder = pathParts[pathParts.length - 2]; // 파일의 부모 폴더
            parentFolder = parentFolder.replaceAll("[_-]", " ");
        }

        String result = cleanName;
        if (!parentFolder.isEmpty() && !isSystemDirectory(new File(parentFolder))) {
            result = cleanName + " " + parentFolder;
        }

        if (result.length() > 50) {
            result = result.substring(0, 50);
        }

        return result.trim();
    }
}
