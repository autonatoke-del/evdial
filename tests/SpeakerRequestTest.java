package ru.autonatoke.evdial;

public final class SpeakerRequestTest {
    private static int checks;
    private static void check(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        SpeakerRequest r=new SpeakerRequest();
        check(!r.selected(null),"Unknown route does not default to speaker");
        check(!r.pending(),"No default pending request");
        r.observe(false,true);
        check(!r.hasChoice(),"Opening the call screen does not change audio");
        r.toggle(null);
        check(r.selected(null)&&r.pending(),"Can select speaker before audio state exists");
        r.observe(null,true);
        check(r.selected(null)&&r.pending(),"Early null callback preserves intent");
        r.observe(false,true);
        check(r.selected(false)&&r.pending(),"Connecting earpiece cannot override the tap");
        r.observe(true,true);
        check(r.selected(true)&&!r.pending()&&r.hasChoice(),"Ringing speaker confirmation retained until answer");
        r.observe(false,false);
        check(r.selected(false)&&r.pending(),"Answer transition can reapply pre-answer selection");
        r.observe(true,false);
        check(!r.pending()&&!r.hasChoice(),"Active confirmation completes the pending request");
        r.observe(false,false);
        check(!r.selected(false),"Later external headset change not overridden");
        r.toggle(false);
        long rev=r.revision();
        r.toggle(false);
        check(!r.selected(false)&&r.revision()>rev,"Rapid double tap switches OFF before callback");
        r.observe(true,true);
        check(!r.selected(true)&&r.pending(),"Stale ON confirmation cannot undo OFF request");
        r.observe(false,true);
        check(!r.pending()&&r.hasChoice(),"OFF intent retained while dialing");
        r.observe(true,false);
        check(r.pending()&&!r.selected(true),"Answer must preserve early OFF selection");
        r.observe(false,false);
        check(!r.hasChoice(),"Confirmed OFF released after connection");
        r.toggle(false);
        rev=r.revision();r.clear();
        check(!r.selected(false)&&!r.pending()&&!r.hasChoice()&&r.revision()>rev,"Call removal clears state and invalidates callbacks");
        check(r.selected(true),"Next call follows its actual route");
        r.toggle(true);r.observe(null,true);
        check(!r.selected(true)&&r.pending(),"Turning speaker OFF works with missing audio data");
        r.clear();
        check(!r.pending(),"Service cleanup cancels pending route");
        System.out.println("PASS: "+checks+" speaker request assertions");
    }
}
