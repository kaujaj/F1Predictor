import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    static final OkHttpClient client = new OkHttpClient();
    static final List<Data> dataList = new ArrayList<>();
    static int year = 2010;

    public static void main(String[] args) throws IOException {
        String url = "https://www.formula1.com/en/results.html/2010/races/860/bahrain/practice-1.html";

        do {
            String body = getBody(url);
            Document document = Jsoup.parse(body);
            List<TrackData> tracksLinks = getTracks(document);
            tracksLinks.forEach(Main::extractData);
            year++;
            url = getNextYearLink(document, year);
            System.out.println(url);
        } while (year < 2021);
        try (BufferedWriter bufferedWriter = Files.newBufferedWriter(Path.of("data.csv"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            bufferedWriter.write("Year,Type,Track,Position,Driver,Car,Time,Laps");
            bufferedWriter.newLine();
            dataList.forEach(d -> {
                try {
                    bufferedWriter.write(d.year + "," + d.type + "," + d.track + "," + d.position + "," + d.driver + "," + d.car + "," + d.time + "," + d.laps);
                    bufferedWriter.newLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

    }

    private static String getNextYearLink(Document document, int year) {
        var elements = document.getElementsByClass("resultsarchive-filter ResultFilterScrollable").first();

        return elements.getElementsByTag("li")
                .stream()
                .flatMap(element -> element.getElementsByTag("a").stream())
                .filter(element -> element.getElementsByTag("span").first().text().equals(year + ""))
                .map(element -> "https://www.formula1.com" + element.attr("href"))
                .findFirst()
                .get();
    }

    private static void extractData(TrackData trackData) {
        extractLapData(trackData, "Practice 1");
        extractLapData(trackData, "Practice 2");
        extractLapData(trackData, "Practice 3");
        extractLapData(trackData, "Qualifying");


    }

    private static void extractLapData(TrackData trackData, String lapType) {
        try {
            String fpLink = getSideNavLink(lapType, trackData.getLink());
            String body = getBody(fpLink);

            Document document = Jsoup.parse(body);
            var table = document.getElementsByClass("resultsarchive-table").first();
            var tableBody = table.getElementsByTag("tbody");
            tableBody.stream()
                    .flatMap(element -> element.getElementsByTag("tr").stream())
                    .forEach(element -> extractDataFromTr(element, lapType, trackData));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("ERROR track Data " + trackData.getTrackName() + " - " + lapType + " - " + trackData.getLink());
        }


    }

    private static void extractDataFromTr(Element element, String type, TrackData trackData) {
        var tds = element.getElementsByTag("td");
        try {
            Data data = new Data();
            data.setYear(year);
            data.setType(type);
            data.setTrack(trackData.getTrackName());
            data.setPosition(Integer.parseInt(tds.get(1).text()));
            data.setDriver(tds.get(3).children().get(0).text() + " " + tds.get(3).children().get(1).text());
            data.setCar(tds.get(4).text());
            if (!type.equals("Qualifying")) {
                data.setTime(getTime(tds.get(5).text()));
                data.setLaps(Integer.parseInt(tds.get(7).text()));
            }


            dataList.add(data);
        } catch (Exception e) {
            //e.printStackTrace();
        }

    }

    private static long getTime(String time) {
        long milliseconds = Long.parseLong(time.split("\\.")[1]);
        milliseconds += Long.parseLong((time.split("\\.")[0]).split(":")[1]) * 60;
        milliseconds += Long.parseLong((time.split("\\.")[0]).split(":")[0]) * 60 * 60;
        return milliseconds;

    }


    private static String getSideNavLink(String navToFind, String link) {
        String body = getBody(link);
        Document document = Jsoup.parse(body);
        var elements = document.getElementsByClass("resultsarchive-side-nav");
        return "https://www.formula1.com" +
                elements.stream()
                        .flatMap(element -> element.getElementsByTag("li").stream())
                        .filter(element -> !element.text().equals("Race"))
                        .flatMap(element -> element.getElementsByTag("a").stream())
                        .filter(element -> element.text().equals(navToFind))
                        .map(element -> element.attr("href"))
                        .findFirst()
                        .get();
    }


    private static List<TrackData> getTracks(Document document) {
        var elements = document.getElementsByClass("resultsarchive-filter ResultFilterScrollable").last();

        return elements.getElementsByTag("li")
                .stream()
                .filter(element -> !element.getElementsByClass("clip").first().text().equals("All"))
                .map(element -> element.getElementsByTag("a"))
                .map(link -> new TrackData(link.first().getElementsByTag("span").first().text(), "https://www.formula1.com" + link.attr("href")))
                .collect(Collectors.toList());

    }


    public static String getBody(String url) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response;
        try {
            response = client.newCall(request).execute();
            return response.body().string();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
