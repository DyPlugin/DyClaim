package dev.dyclaim.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Compares release numbers and beta/RC stages; unknown tags never trigger an update. */
public final class ReleaseVersion implements Comparable<ReleaseVersion> {
    private static final Pattern FORMAT=Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-(beta|rc)(?:[.-](\\d+))?)?$",Pattern.CASE_INSENSITIVE);
    private final int major,minor,patch,stage,sequence;
    private ReleaseVersion(int a,int b,int c,int d,int e){major=a;minor=b;patch=c;stage=d;sequence=e;}
    public static ReleaseVersion parse(String text){
        if(text==null)return null;
        var match=FORMAT.matcher(text.trim());if(!match.matches())return null;
        try{return new ReleaseVersion(Integer.parseInt(match.group(1)),Integer.parseInt(match.group(2)),Integer.parseInt(match.group(3)),match.group(4)==null?2:match.group(4).toLowerCase(Locale.ROOT).equals("rc")?1:0,match.group(5)==null?0:Integer.parseInt(match.group(5)));}
        catch(NumberFormatException ignored){return null;}
    }
    public static boolean newer(String candidate,String installed){var a=parse(candidate);var b=parse(installed);return a!=null&&b!=null&&a.compareTo(b)>0;}
    public int compareTo(ReleaseVersion other){int[] a={major,minor,patch,stage,sequence},b={other.major,other.minor,other.patch,other.stage,other.sequence};for(int i=0;i<a.length;i++){int c=Integer.compare(a[i],b[i]);if(c!=0)return c;}return 0;}
}
