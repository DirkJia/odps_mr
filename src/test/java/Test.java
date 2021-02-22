import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class Test {
    public static void main(String[] args) throws ParseException {
        DateFormat format1 = new SimpleDateFormat("yyyyMMdd");
        System.out.println(format1.getTimeZone());
        System.out.println(format1.parse("20210115").getTime()/1000);
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Cordoba"));
        DateFormat format2 = new SimpleDateFormat("yyyyMMdd", Locale.CHINA);
        System.out.println(format2.parse("20210115").getTime()/1000);
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        DateFormat format3 = new SimpleDateFormat("yyyyMMdd", Locale.US);
        System.out.println(format3.parse("20210115").getTime()/1000);
    }
}
