import com.thebuzzmedia.exiftool.ExifTool;
import com.thebuzzmedia.exiftool.ExifToolBuilder;
import com.thebuzzmedia.exiftool.ExifToolOptions;
import com.thebuzzmedia.exiftool.Tag;
import com.thebuzzmedia.exiftool.core.StandardFormat;
import com.thebuzzmedia.exiftool.core.StandardOptions;
import com.thebuzzmedia.exiftool.core.StandardTag;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.util.*;

public class ImgTool2 {
    static {
//        System.setProperty("exiftool.debug", "true");
    }

    private static final ExifTool exifTool = detect();
    private static ExifToolOptions options = new SupportUtf8FileNameOptions(StandardOptions.builder().withFormat(StandardFormat.NUMERIC).withOverwriteOriginal().build());

    private static ExifTool detect() {
        return new ExifToolBuilder()
                .withPoolSize(10)  // Allow 10 process
                .enableStayOpen()
                .withPath("F:\\software\\exiftool-12.97_64\\exiftool.exe")
                .build();
    }

    public static Map<Tag, String> parse(File image) throws Exception {
        try (ExifTool exifTool = new ExifToolBuilder().build()) {
            return exifTool.getImageMeta(image);
        }
    }

    public static Map<Tag, String> parse(File image, Tag... tags) throws Exception {
        try (ExifTool exifTool = new ExifToolBuilder().build()) {
            return exifTool.getImageMeta(image, Arrays.asList(tags));
        }
    }

    private static Map<Tag, String> parse(String image) throws Exception {
        return parse(new File(image));
    }

    public static void main(String[] args) throws Exception {
        String sourcePath = "D:\\相册\\LR\\相册\\2024元旦\\精修";
        String targetPath = "D:\\相册\\已分类\\202401-元旦-西湖日出";
        File sourceDir = new File(sourcePath);
        File targetDir = new File(targetPath);
        if (!sourceDir.isDirectory() || !sourceDir.canRead()) {
            throw new FileNotFoundException("can not open source dir:" + sourcePath);
        }
        if (!targetDir.isDirectory() || !targetDir.canRead()) {
            throw new FileNotFoundException("can not open target dir:" + targetPath);
        }
        File[] sourceImages = sourceDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".jpg");
            }
        });
        List<StandardTag> standardTags = new ArrayList<>(Arrays.asList(StandardTag.values()));
        standardTags.removeAll(Arrays.asList(
                StandardTag.DIGITAL_ZOOM_RATIO,
                StandardTag.IMAGE_WIDTH,
                StandardTag.IMAGE_HEIGHT,
                StandardTag.X_RESOLUTION,
                StandardTag.Y_RESOLUTION,
                StandardTag.ORIENTATION,
                StandardTag.ROTATION,
                StandardTag.FILE_TYPE,
                StandardTag.FILE_SIZE,
                StandardTag.AVG_BITRATE,
                StandardTag.MIME_TYPE,
                StandardTag.IMAGE_DATA_SIZE,
                StandardTag.MEGA_PIXELS,
                StandardTag.QUALITY));
        System.out.println(standardTags);
        for (final File image : sourceImages) {
            Map<Tag, String> tagMap = exifTool.getImageMeta(image, options, standardTags);
            System.out.println(image.getPath() + "----Tags: " + tagMap);
            System.out.println(image.getName()+" copy to " + targetDir + ": ('n' to skip)");
            String target = new Scanner(System.in).next();
            if ("n".equalsIgnoreCase(target)) {
                continue;
            }
            File targetImg = new File(targetDir + File.separator + target + (target.contains(".") ? "" : ".jpg"));
            tagMap=new HashMap<>(tagMap);
            tagMap.put(StandardTag.DATE_TIME_ORIGINAL,tagMap.get(StandardTag.CREATE_DATE));
            exifTool.setImageMeta(targetImg, options, tagMap);
            targetImg.renameTo(new File(targetDir + File.separator +image.getName()));
            System.out.println("copied to " + targetImg.getPath());
        }
        exifTool.close();
    }
}
