package hr.statistika.tracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Consumer;
import static hr.statistika.tracker.Models.*;

public class MainActivity extends Activity {
    private static final int BLUE=0xff315fe8, GREEN=0xff168365, RED=0xffd04448, ORANGE=0xffb96c18;
    private int bg, surface, ink, muted, line;
    private Store store;
    private Data data;
    private String tab="dashboard", route="main", projectId, clientId, editorClientId, paymentId, returnClientId;
    private String projectQuery="", clientQuery="", dashboardQuery="", projectFilter="Sve";
    private int selectedYear=Domain.today().getYear();
    private boolean largestDebt=true;
    private LinearLayout root, body;
    private ScrollView scroll;
    private final LinkedHashMap<String,View> fields=new LinkedHashMap<>();
    private String pendingExport;
    private final java.util.concurrent.ExecutorService files=java.util.concurrent.Executors.newSingleThreadExecutor();
    private LocalDate renderedDate;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new Store(this);
        try { data=store.load(); }
        catch(RuntimeException e) { new AlertDialog.Builder(this).setTitle("Pohrana nije dostupna").setMessage(e.getMessage()).setPositiveButton("Zatvori",(d,w)->finish()).show(); return; }
        if(state!=null) {
            tab=state.getString("tab","dashboard"); route=state.getString("route","main");
            projectId=state.getString("projectId"); clientId=state.getString("clientId"); editorClientId=state.getString("editorClientId");
            paymentId=state.getString("paymentId"); returnClientId=state.getString("returnClientId");
            selectedYear=state.getInt("year",selectedYear); projectFilter=state.getString("filter","Sve");
            projectQuery=state.getString("projectQuery",""); clientQuery=state.getString("clientQuery",""); dashboardQuery=state.getString("dashboardQuery","");
            largestDebt=state.getBoolean("largestDebt",true); pendingExport=state.getString("pendingExport");
        }
        render();
        if(state!=null) {
            Bundle saved=state.getBundle("fields");
            if(saved!=null) for(Map.Entry<String,View> entry:fields.entrySet()) {
                View v=entry.getValue(); String key=entry.getKey();
                if(v instanceof EditText && saved.containsKey(key)) ((EditText)v).setText(saved.getString(key));
                else if(v instanceof Spinner) ((Spinner)v).setSelection(saved.getInt(key,0));
                else if(v instanceof Switch) ((Switch)v).setChecked(saved.getBoolean(key,false));
            }
            int position=state.getInt("scroll",0); scroll.post(()->scroll.scrollTo(0,position));
        }
    }
    @Override protected void onResume() { super.onResume(); if(data!=null && route.equals("main") && !Domain.today().equals(renderedDate)) render(); }
    @Override protected void onDestroy() { super.onDestroy(); if(store!=null) store.close(); files.shutdown(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("tab",tab); state.putString("route",route); state.putString("projectId",projectId); state.putString("clientId",clientId);
        state.putString("editorClientId",editorClientId); state.putString("paymentId",paymentId); state.putString("returnClientId",returnClientId);
        state.putInt("year",selectedYear); state.putString("filter",projectFilter); state.putString("projectQuery",projectQuery);
        state.putString("clientQuery",clientQuery); state.putString("dashboardQuery",dashboardQuery); state.putBoolean("largestDebt",largestDebt);
        state.putString("pendingExport",pendingExport); state.putInt("scroll",scroll==null?0:scroll.getScrollY());
        Bundle saved=new Bundle();
        for(Map.Entry<String,View> entry:fields.entrySet()) {
            View v=entry.getValue(); String key=entry.getKey();
            if(v instanceof EditText) saved.putString(key,((EditText)v).getText().toString());
            else if(v instanceof Spinner) saved.putInt(key,((Spinner)v).getSelectedItemPosition());
            else if(v instanceof Switch) saved.putBoolean(key,((Switch)v).isChecked());
        }
        state.putBundle("fields",saved);
    }
    private void colors() {
        boolean dark=data.settings.darkMode;
        bg=dark?0xff111724:0xfff5f6fa; surface=dark?0xff1c2536:Color.WHITE;
        ink=dark?0xffedf1fa:0xff202a3c; muted=dark?0xffa9b5ca:0xff727f94; line=dark?0xff334056:0xffe6eaf2;
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(surface);
        getWindow().getDecorView().setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }
    private void render() {
        renderedDate=Domain.today();
        colors(); fields.clear();
        root=column(); root.setBackgroundColor(bg); root.setFitsSystemWindows(true);
        root.setOnApplyWindowInsetsListener((view,insets)-> {
            if(android.os.Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom); return WindowInsets.CONSUMED;
            }
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(root); root.requestApplyInsets();
        LinearLayout toolbar=row(); toolbar.setPadding(dp(22),dp(14),dp(22),dp(12)); toolbar.setGravity(Gravity.CENTER_VERTICAL);
        if(!route.equals("main")) {
            toolbar.addView(iconButton("back","Natrag",this::onBackPressed));
            TextView title=text(routeTitle(),19,ink,true); LinearLayout.LayoutParams titleParams=new LinearLayout.LayoutParams(0,-2,1); titleParams.leftMargin=dp(10); toolbar.addView(title,titleParams);
        } else {
            ImageView logo=new ImageView(this);logo.setImageResource(hr.statistika.tracker.R.mipmap.ic_launcher);logo.setContentDescription(getString(hr.statistika.tracker.R.string.app_name));
            toolbar.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
            LinearLayout brand=column(); brand.setPadding(dp(10),0,0,0); brand.addView(text(getString(hr.statistika.tracker.R.string.app_name),14,ink,true)); brand.addView(text("Vaš poslovni pregled",11,muted,false));
            toolbar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(toolbar);
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        body=column(); body.setPadding(dp(20),dp(10),dp(20),dp(28)); scroll.addView(body); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        switch(route) {
            case "project": projectDetail(); break;
            case "projectForm": projectForm(); break;
            case "paymentForm": paymentForm(); break;
            case "client": clientDetail(); break;
            case "clientForm": clientForm(); break;
            default:
                switch(tab) { case "projects": projects(); break; case "clients": clients(); break; case "analytics": analytics(); break; case "settings": settings(); break; default: dashboard(); }
                bottomNav();
        }
    }
    private String routeTitle() {
        switch(route) { case "project": return "Detalji projekta"; case "projectForm": return projectId==null?"Novi projekt":"Uredi projekt";
            case "paymentForm": return paymentId==null?"Nova uplata":"Uredi uplatu"; case "client": return "Detalji klijenta";
            case "clientForm": return clientId==null?"Novi klijent":"Uredi klijenta"; default: return "Pregled"; }
    }
    private void bottomNav() {
        LinearLayout nav=row(); nav.setBackgroundColor(surface); nav.setPadding(dp(4),dp(9),dp(4),dp(8)); nav.setElevation(dp(7));
        String[] ids={"dashboard","projects","clients","analytics","settings"}, labels={"Pregled","Projekti","Klijenti","Analitika","Postavke"};
        for(int i=0;i<ids.length;i++) {
            String id=ids[i]; boolean selected=tab.equals(id); LinearLayout item=column(); item.setGravity(Gravity.CENTER); item.setMinimumHeight(dp(54));
            LinearLayout iconBox=column(); iconBox.setGravity(Gravity.CENTER); iconBox.setBackground(shape(selected?tint(BLUE):surface,18));
            iconBox.addView(new IconView(this,id,selected?BLUE:muted),new LinearLayout.LayoutParams(dp(22),dp(22)));
            item.addView(iconBox,new LinearLayout.LayoutParams(dp(48),dp(30)));
            TextView label=text(labels[i],10,selected?BLUE:muted,selected); label.setGravity(Gravity.CENTER); label.setPadding(0,dp(5),0,0); item.addView(label,new LinearLayout.LayoutParams(-1,-2));
            item.setContentDescription(labels[i]); item.setOnClickListener(v->{ hideKeyboard(); tab=id; route="main"; returnClientId=null; render(); });
            nav.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav);
    }
    private void dashboard() {
        LocalDate today=Domain.today();
        String date=today.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",Locale.forLanguageTag("hr-HR")));
        body.addView(text(date.toUpperCase(Locale.forLanguageTag("hr-HR")),11,muted,true)); gap(body,8);
        body.addView(text("Sve pod kontrolom.",28,ink,true)); gap(body,5); body.addView(text("Vaši projekti, rokovi i naplata na jednom mjestu.",13,muted,false)); gap(body,22);
        LinearLayout hero=column(); hero.setPadding(dp(20),dp(20),dp(20),dp(20)); hero.setBackground(shape(BLUE,20));
        LinearLayout heroTop=row(); heroTop.setGravity(Gravity.CENTER_VERTICAL); heroTop.addView(text("UKUPNO NENAPLAĆENO",11,0xffd7e2ff,true),new LinearLayout.LayoutParams(0,-2,1)); heroTop.addView(new IconView(this,"wallet",0xffd7e2ff),new LinearLayout.LayoutParams(dp(22),dp(22))); hero.addView(heroTop);
        TextView total=text(Domain.money(Domain.outstanding(data)),34,Color.WHITE,true); total.setPadding(0,dp(10),0,dp(6)); hero.addView(total);
        long owing=data.visibleProjects().stream().filter(p->Domain.remaining(data,p)>0).count();
        hero.addView(text(owing+" projekata s preostalim iznosom",12,0xffd7e2ff,false));
        long unknown=data.visibleProjects().stream().filter(p->p.price==null).count();
        if(unknown>0) hero.addView(text(unknown+" bez definirane cijene · nisu uključeni",11,0xffd7e2ff,false));
        hero.setOnClickListener(v->{tab="projects"; projectFilter="Dugovanja"; projectQuery=""; render();}); body.addView(hero); gap(body,12);
        long late=data.visibleProjects().stream().filter(p->Domain.overdue(p,today)).count();
        if(late>0) {body.addView(button(late+" projekata kasni · Pogledaj rokove",tint(RED),RED,()->{tab="projects";projectFilter="Kasni";projectQuery="";render();}));gap(body,12);}
        long active=data.visibleProjects().stream().filter(Domain::active).count();
        LocalDate end=today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        long week=data.visibleProjects().stream().filter(Domain::active).filter(p->!LocalDate.parse(p.deadline).isBefore(today)&&!LocalDate.parse(p.deadline).isAfter(end)).count();
        long waiting=data.visibleProjects().stream().filter(p->p.status==ProjectStatus.DONE&&Domain.remaining(data,p)>0).count();
        LinearLayout metrics=row(); metrics.addView(metric("Aktivni projekti",String.valueOf(active),"projects",BLUE),weighted(6)); metrics.addView(metric("Rok ovaj tjedan",String.valueOf(week),"clock",ORANGE),weighted(0)); body.addView(metrics); gap(body,10);
        LinearLayout metrics2=row(); metrics2.addView(metric("Čeka naplatu",String.valueOf(waiting),"wallet",RED),weighted(6)); metrics2.addView(metric("Naplaćeno ovaj mjesec",Domain.money(Domain.monthlyRevenue(data,today.getYear())[today.getMonthValue()-1]),"analytics",GREEN),weighted(0)); body.addView(metrics2); gap(body,20);
        body.addView(button("+  Novi projekt",BLUE,Color.WHITE,()->openProjectForm(null)),new LinearLayout.LayoutParams(-1,dp(50))); gap(body,18);
        LinearLayout lists=column();
        body.addView(search("Pretraži klijente i projekte",dashboardQuery,value->{ dashboardQuery=value; renderDashboardLists(lists); })); gap(body,12); body.addView(lists); renderDashboardLists(lists);
    }
    private LinearLayout metric(String label,String value,String icon,int color) {
        LinearLayout card=card(); LinearLayout head=row(); head.setGravity(Gravity.CENTER_VERTICAL); head.addView(new IconView(this,icon,color),new LinearLayout.LayoutParams(dp(18),dp(18)));
        TextView name=text(label,10,muted,false); name.setPadding(dp(7),0,0,0); head.addView(name,new LinearLayout.LayoutParams(0,-2,1)); card.addView(head); gap(card,9); card.addView(text(value,22,ink,true)); return card;
    }
    private void renderDashboardLists(LinearLayout lists) {
        lists.removeAllViews(); List<Project> sorted=data.visibleProjects(); sorted.sort(Comparator.comparing(p->p.deadline));
        if(!dashboardQuery.trim().isEmpty()) {
            section(lists,"Rezultati pretrage",sorted.stream().filter(p->Domain.matches(data,p,dashboardQuery)).collect(java.util.stream.Collectors.toList()),"Nema pronađenih projekata.");
            List<Client> matching=data.clients.stream().filter(c->Domain.normalize(c.name()+" "+c.institution).contains(Domain.normalize(dashboardQuery))).collect(java.util.stream.Collectors.toList());
            if(!matching.isEmpty()) { heading(lists,"Klijenti",matching.size()); for(Client c:matching) { lists.addView(clientCard(c)); gap(lists,10); } }
            return;
        }
        section(lists,"Za započeti",sorted.stream().filter(p->p.status==ProjectStatus.TODO).collect(java.util.stream.Collectors.toList()),"Nema projekata za započeti.");
        section(lists,"U tijeku",sorted.stream().filter(p->p.status==ProjectStatus.IN_PROGRESS).collect(java.util.stream.Collectors.toList()),"Nema projekata u tijeku.");
        List<Project> debts=new ArrayList<>(sorted.stream().filter(p->p.status==ProjectStatus.DONE&&Domain.remaining(data,p)>0).collect(java.util.stream.Collectors.toList()));
        if(largestDebt) debts.sort((a,b)->Long.compare(Domain.remaining(data,b),Domain.remaining(data,a)));
        else debts.sort(Comparator.comparing(p->p.completedDate==null?p.createdDate:p.completedDate));
        LinearLayout sort=row(); sort.setGravity(Gravity.CENTER_VERTICAL); sort.addView(text("ČEKA NAPLATU",12,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView order=text(largestDebt?"Najveći dug ↓":"Najstariji završeni ↓",11,BLUE,true); order.setPadding(dp(4),dp(14),0,dp(14));
        order.setOnClickListener(v->{largestDebt=!largestDebt; renderDashboardLists(lists);}); sort.addView(order); lists.addView(sort);
        if(debts.isEmpty()) empty(lists,"Sve završeno je plaćeno.","Evidentirane uplate automatski ažuriraju ovaj pregled.","check");
        else for(Project p:debts) { lists.addView(projectCard(p)); gap(lists,10); }
        List<Project> unknown=sorted.stream().filter(p->p.status==ProjectStatus.DONE&&p.price==null).collect(java.util.stream.Collectors.toList());
        if(!unknown.isEmpty()) section(lists,"Završeno · definirajte cijenu",unknown,"" );
        List<Project> done=new ArrayList<>(sorted.stream().filter(p->p.status==ProjectStatus.DONE&&Domain.paymentStatus(data,p)==PaymentStatus.PAID).collect(java.util.stream.Collectors.toList()));
        done.sort(Comparator.comparing((Project p)->p.completedDate==null?p.createdDate:p.completedDate).reversed());
        section(lists,"Završeno i plaćeno",done.stream().limit(3).collect(java.util.stream.Collectors.toList()),"Još nema završenih i plaćenih projekata.");
        if(done.size()>3) lists.addView(button("Pogledaj sve završene",surface,BLUE,()->{tab="projects"; projectFilter="Završeno";render();}));
    }
    private void section(LinearLayout parent,String title,List<Project> projects,String emptyTitle) {
        heading(parent,title,projects.size());
        if(projects.isEmpty()) empty(parent,emptyTitle,"Dodajte projekt ili promijenite njegov status.","projects");
        else for(Project p:projects) { parent.addView(projectCard(p)); gap(parent,10); }
        gap(parent,8);
    }
    private void heading(LinearLayout parent,String title,int count) {
        LinearLayout row=row(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(14),0,dp(12));
        row.addView(text(title.toUpperCase(Locale.forLanguageTag("hr-HR")),12,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=text(String.valueOf(count),11,muted,true); badge.setPadding(dp(9),dp(3),dp(9),dp(3)); badge.setBackground(shape(line,12)); row.addView(badge); parent.addView(row);
    }
    private LinearLayout projectCard(Project p) {
        LinearLayout card=card(); Client client=data.client(p.clientId); boolean active=Domain.active(p); long days=Domain.days(p,Domain.today());
        int urgency=active&&days<0?RED:active&&days<=2?RED:active&&days<=7?ORANGE:muted;
        card.setBackground(bordered(surface,16,active&&days<0?0xffecc3c7:line));
        LinearLayout head=row(); head.setGravity(Gravity.CENTER_VERTICAL); head.addView(text(client==null?"Klijent":client.name(),16,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        head.addView(badge(paymentLabel(Domain.paymentStatus(data,p)),paymentColor(Domain.paymentStatus(data,p)))); card.addView(head); gap(card,5);
        card.addView(text(Domain.title(data,p),12,muted,false)); gap(card,12);
        LinearLayout deadline=row(); deadline.setGravity(Gravity.CENTER_VERTICAL); deadline.addView(new IconView(this,"clock",urgency),new LinearLayout.LayoutParams(dp(15),dp(15)));
        TextView date=text((active?Domain.deadlineLabel(p,Domain.today()):statusLabel(p.status))+" · "+Domain.date(p.deadline),11,urgency,active&&days<=7); date.setPadding(dp(6),0,0,0); deadline.addView(date); card.addView(deadline); gap(card,12);
        LinearLayout finances=row(); finances.addView(text("Uplaćeno "+Domain.money(Domain.paid(data,p)),12,muted,false),new LinearLayout.LayoutParams(0,-2,1));
        finances.addView(text(p.price==null?"Cijena nije definirana":"od "+Domain.money(p.price),12,muted,false)); card.addView(finances);
        if(p.price!=null && p.price>0) { ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal); bar.setMax(1000); bar.setProgress((int)Math.min(1000,Domain.paid(data,p)*1000/p.price)); bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Domain.remaining(data,p)==0?GREEN:BLUE)); bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(line)); LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(5)); bp.topMargin=dp(8); bp.bottomMargin=dp(10); card.addView(bar,bp); }
        else gap(card,10);
        LinearLayout footer=row(); footer.setGravity(Gravity.CENTER_VERTICAL);
        long over=Domain.overpayment(data,p); String remaining=p.price==null?"Cijena nije definirana":over>0?"Preplaćeno +"+Domain.money(over):"Preostalo "+Domain.money(Domain.remaining(data,p));
        footer.addView(text(remaining,13,Domain.remaining(data,p)>0?ink:GREEN,true),new LinearLayout.LayoutParams(0,-2,1));
        if(p.status==ProjectStatus.TODO) footer.addView(smallButton("Započni",()->setStatus(p,ProjectStatus.IN_PROGRESS)));
        else if(p.status==ProjectStatus.IN_PROGRESS) footer.addView(smallButton("Završi",()->setStatus(p,ProjectStatus.DONE)));
        else if(Domain.remaining(data,p)>0) footer.addView(smallButton("+ Uplata",()->openPaymentForm(p,null)));
        else footer.addView(new IconView(this,"arrow",muted),new LinearLayout.LayoutParams(dp(18),dp(18)));
        card.addView(footer); card.setContentDescription((client==null?"":client.name())+", "+Domain.title(data,p)+", "+statusLabel(p.status)+", "+remaining);
        card.setOnClickListener(v->openProject(p)); return card;
    }
    private void projects() {
        pageHeading("Projekti","Sve obaveze i povijest na jednom mjestu.");
        body.addView(button("+  Novi projekt",BLUE,Color.WHITE,()->openProjectForm(null))); gap(body,16);
        LinearLayout list=column(); body.addView(search("Klijent, projekt ili institucija",projectQuery,value->{projectQuery=value;renderProjectList(list);})); gap(body,12);
        HorizontalScrollView filters=new HorizontalScrollView(this); filters.setHorizontalScrollBarEnabled(false); LinearLayout chips=row();
        String[] labels={"Sve","Za započeti","U tijeku","Završeno","Arhivirano","Neplaćeno","Djelomično","Plaćeno","Kasni","Dugovanja"};
        for(String label:labels) { TextView chip=badge(label,projectFilter.equals(label)?BLUE:muted); chip.setPadding(dp(14),dp(12),dp(14),dp(12)); chip.setOnClickListener(v->{projectFilter=label; hideKeyboard(); render();}); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44)); lp.rightMargin=dp(8); chips.addView(chip,lp); }
        filters.addView(chips); body.addView(filters); gap(body,12); body.addView(list); renderProjectList(list);
    }
    private void renderProjectList(LinearLayout list) {
        list.removeAllViews(); List<Project> projects=data.visibleProjects(); projects.sort(Comparator.comparing(p->p.deadline)); int count=0;
        for(Project p:projects) if(projectMatchesFilter(p) && Domain.matches(data,p,projectQuery)) { list.addView(projectCard(p)); gap(list,12); count++; }
        if(count==0) empty(list,"Nema projekata za ovaj prikaz.","Promijenite filter ili dodajte novi projekt.","projects");
    }
    private boolean projectMatchesFilter(Project p) {
        switch(projectFilter) {
            case "Za započeti": return p.status==ProjectStatus.TODO; case "U tijeku": return p.status==ProjectStatus.IN_PROGRESS;
            case "Završeno": return p.status==ProjectStatus.DONE; case "Arhivirano": return p.status==ProjectStatus.ARCHIVED;
            case "Neplaćeno": return Domain.paymentStatus(data,p)==PaymentStatus.NOT_PAID; case "Djelomično": return Domain.paymentStatus(data,p)==PaymentStatus.PARTIALLY_PAID;
            case "Plaćeno": return Domain.paymentStatus(data,p)==PaymentStatus.PAID; case "Kasni": return Domain.overdue(p,Domain.today());
            case "Dugovanja": return Domain.remaining(data,p)>0; default: return true;
        }
    }
    private void clients() {
        pageHeading("Klijenti","Ljudi s kojima stvarate rezultate.");
        body.addView(button("+  Novi klijent",BLUE,Color.WHITE,()->{clientId=null;route="clientForm";render();})); gap(body,16);
        LinearLayout list=column(); body.addView(search("Ime, prezime ili institucija",clientQuery,value->{clientQuery=value;renderClientList(list);})); gap(body,16); body.addView(list); renderClientList(list);
    }
    private void renderClientList(LinearLayout list) {
        list.removeAllViews(); List<Client> sorted=new ArrayList<>(data.clients); sorted.sort(Comparator.comparing(c->Domain.normalize(c.lastName+" "+c.firstName))); int count=0;
        for(Client c:sorted) if(Domain.normalize(c.name()+" "+c.institution).contains(Domain.normalize(clientQuery))) { list.addView(clientCard(c));gap(list,12);count++; }
        if(count==0) empty(list,"Još nema klijenata.","Dodajte prvog klijenta ili ga unesite uz novi projekt.","clients");
    }
    private LinearLayout clientCard(Client c) {
        LinearLayout card=card(); LinearLayout head=row(); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView initials=text(c.firstName.substring(0,1)+c.lastName.substring(0,1),14,BLUE,true); initials.setGravity(Gravity.CENTER); initials.setBackground(shape(tint(BLUE),14)); head.addView(initials,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout name=column(); name.setPadding(dp(12),0,0,0); name.addView(text(c.name(),16,ink,true)); if(!c.institution.trim().isEmpty()) name.addView(text(c.institution,11,muted,false)); head.addView(name,new LinearLayout.LayoutParams(0,-2,1)); head.addView(new IconView(this,"arrow",muted),new LinearLayout.LayoutParams(dp(18),dp(18))); card.addView(head); gap(card,14);
        List<Project> projects=data.visibleProjects().stream().filter(p->p.clientId.equals(c.id)).collect(java.util.stream.Collectors.toList()); long price=0,paid=0;
        for(Project p:projects) { if(p.price!=null)price+=p.price;paid+=Domain.paid(data,p); }
        LinearLayout metrics=row(); metrics.addView(text(projects.size()+" projekata",11,muted,false),new LinearLayout.LayoutParams(0,-2,1)); metrics.addView(text("Vrijednost "+Domain.money(price),11,muted,false)); card.addView(metrics); gap(card,5); card.addView(text("Naplaćeno "+Domain.money(paid),12,GREEN,true));
        card.setOnClickListener(v->{clientId=c.id;route="client";render();}); return card;
    }
    private void clientDetail() {
        Client c=data.client(clientId); if(c==null){route="main";render();return;}
        body.addView(text(c.name(),28,ink,true)); gap(body,8); body.addView(button("Uredi klijenta",surface,BLUE,()->{route="clientForm";render();})); gap(body,18);
        LinearLayout contact=card(); value(contact,"Email",c.email.trim().isEmpty()?"—":c.email); value(contact,"Telefon",c.phone.trim().isEmpty()?"—":c.phone); value(contact,"Institucija",c.institution.trim().isEmpty()?"—":c.institution); if(!c.notes.trim().isEmpty())value(contact,"Napomena",c.notes); body.addView(contact); gap(body,16);
        body.addView(button("+  Projekt za ovog klijenta",BLUE,Color.WHITE,()->{returnClientId=c.id; projectId=null;editorClientId=c.id;route="projectForm";render();}));
        List<Project> projects=new ArrayList<>(data.visibleProjects().stream().filter(p->p.clientId.equals(c.id)).collect(java.util.stream.Collectors.toList())); projects.sort(Comparator.comparing(p->p.deadline)); section(body,"Projekti klijenta",projects,"Klijent još nema projekata.");
    }
    private void projectDetail() {
        Project p=data.project(projectId); if(p==null||p.deletedAt!=null){route="main";render();return;}
        Client c=data.client(p.clientId); body.addView(text(Domain.title(data,p),25,ink,true)); gap(body,7);
        TextView client=text(c.name()+"  →",14,BLUE,true); client.setPadding(0,dp(6),0,dp(12)); client.setOnClickListener(v->{clientId=c.id;route="client";render();}); body.addView(client);
        LinearLayout badges=row(); badges.addView(badge(statusLabel(p.status),statusColor(p.status))); TextView payBadge=badge(paymentLabel(Domain.paymentStatus(data,p)),paymentColor(Domain.paymentStatus(data,p))); LinearLayout.LayoutParams badgeParams=new LinearLayout.LayoutParams(-2,-2); badgeParams.leftMargin=dp(8); badges.addView(payBadge,badgeParams); body.addView(badges); gap(body,18);
        LinearLayout finance=card(); finance.addView(text("FINANCIJE",11,muted,true)); gap(finance,12); value(finance,"Ukupna cijena",p.price==null?"Cijena nije definirana":Domain.money(p.price)); value(finance,"Ukupno uplaćeno",Domain.money(Domain.paid(data,p))); value(finance,"Preostalo",p.price==null?"—":Domain.money(Domain.remaining(data,p)));
        if(Domain.overpayment(data,p)>0) value(finance,"Preplaćeno","+"+Domain.money(Domain.overpayment(data,p))); body.addView(finance); gap(body,12);
        LinearLayout paymentActions=row(); paymentActions.addView(button("+  Uplata",BLUE,Color.WHITE,()->openPaymentForm(p,null)),weighted(6));
        if(p.price!=null&&Domain.remaining(data,p)>0) paymentActions.addView(button("Označi plaćeno",tint(GREEN),GREEN,()->quickPay(p)),weighted(0)); body.addView(paymentActions); gap(body,18);
        LinearLayout project=card(); value(project,"Datum zaprimanja",Domain.date(p.createdDate)); value(project,"Rok",Domain.date(p.deadline)+(Domain.active(p)?" · "+Domain.deadlineLabel(p,Domain.today()):"")); if(p.completedDate!=null)value(project,"Završeno",Domain.date(p.completedDate)); if(!p.notes.trim().isEmpty())value(project,"Napomena",p.notes); body.addView(project); gap(body,12);
        if(p.status==ProjectStatus.TODO) body.addView(button("Započni rad",BLUE,Color.WHITE,()->setStatus(p,ProjectStatus.IN_PROGRESS)));
        else if(p.status==ProjectStatus.IN_PROGRESS) body.addView(button("Označi završeno",GREEN,Color.WHITE,()->setStatus(p,ProjectStatus.DONE)));
        else if(p.status==ProjectStatus.DONE) body.addView(button("Arhiviraj projekt",surface,BLUE,()->setStatus(p,ProjectStatus.ARCHIVED)));
        else body.addView(button("Vrati u završene",surface,BLUE,()->setStatus(p,ProjectStatus.DONE)));
        gap(body,10); body.addView(button("Uredi projekt",surface,BLUE,()->openProjectForm(p))); gap(body,16);
        heading(body,"Uplate",data.paymentsFor(p.id).size());
        if(data.paymentsFor(p.id).isEmpty()) empty(body,"Još nema uplata.","Polog i svaka sljedeća uplata vode se zasebno.","wallet");
        for(Payment payment:data.paymentsFor(p.id)) {
            LinearLayout entry=card(); LinearLayout top=row(); top.addView(text(paymentTypeLabel(payment.type),14,ink,true),new LinearLayout.LayoutParams(0,-2,1)); top.addView(text(Domain.money(payment.amount),16,GREEN,true)); entry.addView(top); gap(entry,6); entry.addView(text(Domain.date(payment.paymentDate)+" · Dodirnite za uređivanje",11,muted,false)); if(!payment.notes.trim().isEmpty()){gap(entry,5);entry.addView(text(payment.notes,12,muted,false));}
            entry.setOnClickListener(v->openPaymentForm(p,payment)); body.addView(entry);gap(body,10);
        }
        gap(body,18); body.addView(button("Obriši projekt",tint(RED),RED,()->confirmDelete(p)));
    }
    private void analytics() {
        pageHeading("Analitika","Stvarna naplata prema datumu uplate.");
        TreeSet<Integer> years=new TreeSet<>(Comparator.reverseOrder()); years.add(Domain.today().getYear()); years.add(selectedYear);
        for(Payment p:data.payments) years.add(LocalDate.parse(p.paymentDate).getYear()); for(Project p:data.visibleProjects())years.add(LocalDate.parse(p.createdDate).getYear());
        for(int year=Domain.today().getYear()-3;year<=Domain.today().getYear()+1;year++)years.add(year);
        List<Integer> options=new ArrayList<>(years); LinearLayout yearRow=row(); yearRow.setGravity(Gravity.CENTER_VERTICAL); yearRow.addView(text("Odabrana godina",13,muted,false),new LinearLayout.LayoutParams(0,-2,1));
        Spinner select=new Spinner(this); List<String> labels=options.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList()); select.setAdapter(adapter(labels)); select.setSelection(options.indexOf(selectedYear)); yearRow.addView(select,new LinearLayout.LayoutParams(dp(110),dp(48))); body.addView(yearRow); gap(body,12);
        select.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){ public void onNothingSelected(AdapterView<?> p){} public void onItemSelected(AdapterView<?> p,View v,int pos,long id){int year=options.get(pos);if(year!=selectedYear){selectedYear=year;render();}}});
        long[] months=Domain.monthlyRevenue(data,selectedYear); long total=0; for(long amount:months)total+=amount;
        LinearLayout hero=card(); hero.addView(text("NAPLAĆENO U "+selectedYear+".",11,muted,true)); gap(hero,7);hero.addView(text(Domain.money(total),32,BLUE,true));gap(hero,7);hero.addView(text("Zbroj evidentiranih uplata, uključujući pologe.",12,muted,false));body.addView(hero);gap(body,14);
        LinearLayout chart=card();chart.addView(text("Mjesečna naplata",17,ink,true));gap(chart,4);TextView chosen=text("Dodirnite mjesec za točan iznos",11,muted,false);chart.addView(chosen);
        RevenueChart plot=new RevenueChart(this,months,muted,line);plot.onMonthSelected(index->chosen.setText(RevenueChart.MONTHS[index]+" "+selectedYear+": "+Domain.money(months[index])));chart.addView(plot,new LinearLayout.LayoutParams(-1,dp(240)));body.addView(chart);gap(body,12);
        if(total==0){empty(body,"Nema uplata u odabranoj godini.","Graf uvijek prikazuje svih 12 mjeseci.","analytics");gap(body,12);}
        LinearLayout a=row();a.addView(metric("Naplaćeno ovaj mjesec",Domain.money(Domain.monthlyRevenue(data,Domain.today().getYear())[Domain.today().getMonthValue()-1]),"wallet",GREEN),weighted(6));a.addView(metric("Ukupno nenaplaćeno",Domain.money(Domain.outstanding(data)),"clock",ORANGE),weighted(0));body.addView(a);gap(body,12);
        long count=data.visibleProjects().stream().filter(p->LocalDate.parse(p.createdDate).getYear()==selectedYear).count();LinearLayout b=row();b.addView(metric("Prosječna cijena · "+selectedYear,Domain.money(Domain.averagePrice(data,selectedYear)),"projects",BLUE),weighted(6));b.addView(metric("Novi projekti · "+selectedYear,String.valueOf(count),"projects",BLUE),weighted(0));body.addView(b);gap(body,18);
        LinearLayout table=card();table.addView(text("Pregled po mjesecima",16,ink,true));gap(table,10);for(int i=0;i<12;i++)value(table,RevenueChart.MONTHS[i],Domain.money(months[i]));body.addView(table);
    }

    private void projectForm() {
        Project original=projectId==null?null:data.project(projectId);
        if(original!=null && editorClientId==null)editorClientId=original.clientId;
        body.addView(text(original==null?"Novi posao, jasan plan.":"Ažurirajte detalje projekta.",22,ink,true));gap(body,6);body.addView(text("Datum zaprimanja novog projekta postavlja se automatski.",12,muted,false));gap(body,22);
        LinearLayout form=card(); label(form,"Postojeći klijent"); AutoCompleteTextView lookup=new AutoCompleteTextView(this);styleInput(lookup,"Pretražite ime klijenta");lookup.setThreshold(1);fields.put("clientLookup",lookup);
        List<Client> candidates=new ArrayList<>(data.clients);candidates.sort(Comparator.comparing(Client::name));List<String> names=candidates.stream().map(c->c.name()+(!c.institution.trim().isEmpty()?" · "+c.institution:"")).collect(java.util.stream.Collectors.toList());lookup.setAdapter(adapter(names));form.addView(lookup,new LinearLayout.LayoutParams(-1,dp(50)));gap(form,14);
        Client selected=editorClientId==null?null:data.client(editorClientId);
        EditText first=field(form,"Ime *",selected==null?"":selected.firstName,"Ime klijenta",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText last=field(form,"Prezime *",selected==null?"":selected.lastName,"Prezime klijenta",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        if(selected!=null){lookup.setText(names.get(candidates.indexOf(selected)),false);first.setEnabled(false);last.setEnabled(false);}
        lookup.setOnItemClickListener((parent,view,pos,id)->{
            int index=names.indexOf(parent.getItemAtPosition(pos).toString());if(index>=0){Client c=candidates.get(index);editorClientId=c.id;first.setText(c.firstName);last.setText(c.lastName);first.setEnabled(false);last.setEnabled(false);hideKeyboard();}
        });
        watch(lookup,value->{Client c=editorClientId==null?null:data.client(editorClientId);String full=c==null?"":c.name()+(!c.institution.trim().isEmpty()?" · "+c.institution:"");if(c!=null&&!value.equals(full)){editorClientId=null;first.setEnabled(true);last.setEnabled(true);first.setText("");last.setText("");}});
        TextView clientHint=text("Za novog klijenta ostavite pretragu praznom i unesite ime i prezime.",11,muted,false);form.addView(clientHint);gap(form,16);
        EditText title=field(form,"Naziv projekta",original==null?"":original.title,"Npr. analiza diplomskog rada",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText created=original==null?null:dateField(form,"Datum zaprimanja *",original.createdDate);
        EditText deadline=dateField(form,"Rok *",original==null?Domain.today().plusDays(7).toString():original.deadline);
        EditText price=field(form,"Ukupna cijena (€)",original==null?"":Domain.inputMoney(original.price),"Ostavite prazno ako još nije poznata",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        Spinner status=original==null?null:spinner(form,"Status projekta",Arrays.stream(ProjectStatus.values()).map(this::statusLabel).collect(java.util.stream.Collectors.toList()),original.status.ordinal());
        Switch deposit=null;EditText depositAmount=null;
        if(original==null) {
            deposit=new Switch(this);deposit.setText("Polog uplaćen?");deposit.setTextSize(14);deposit.setTextColor(ink);deposit.setPadding(0,dp(10),0,dp(10));deposit.setMinimumHeight(dp(48));fields.put("deposit",deposit);form.addView(deposit);
            LinearLayout depositBox=column();depositAmount=field(depositBox,"Iznos pologa (€) *","","0,00",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);form.addView(depositBox);depositBox.setVisibility(View.GONE);deposit.setOnCheckedChangeListener((v,on)->depositBox.setVisibility(on?View.VISIBLE:View.GONE));
        }
        EditText notes=notes(form,original==null?"":original.notes);body.addView(form);gap(body,20);
        Switch paidDeposit=deposit;EditText depositInput=depositAmount;
        body.addView(button("Spremi projekt",BLUE,Color.WHITE,()-> {
            try {
                String firstName=required(first),lastName=required(last);String deadlineValue=validDate(deadline);String createdValue=created==null?Domain.today().toString():validDate(created);
                Long priceValue=price.getText().toString().trim().isEmpty()?null:validMoney(price,false);
                long amount=paidDeposit!=null&&paidDeposit.isChecked()?validMoney(depositInput,true):0;
                Client chosen=editorClientId==null?null:data.client(editorClientId);
                if(chosen==null) chosen=data.clients.stream().filter(c->Domain.normalize(c.firstName).equals(Domain.normalize(firstName))&&Domain.normalize(c.lastName).equals(Domain.normalize(lastName))).findFirst().orElse(null);
                boolean newClient=chosen==null;if(newClient){chosen=new Client();chosen.firstName=firstName;chosen.lastName=lastName;}
                Client c=chosen;Project p=original==null?new Project():Backup.readProject(Backup.projectJson(original));p.clientId=c.id;p.title=title.getText().toString().trim();p.deadline=deadlineValue;p.createdDate=createdValue;p.price=priceValue;p.notes=notes.getText().toString().trim();p.updatedAt=Domain.timestamp();
                if(status!=null)Domain.changeStatus(p,ProjectStatus.values()[status.getSelectedItemPosition()],Domain.today());
                if(commit(()->{if(newClient)data.clients.add(c);if(original!=null)data.projects.remove(original);data.projects.add(p);if(amount>0){Payment payment=new Payment();payment.projectId=p.id;payment.amount=amount;payment.type=PaymentType.DEPOSIT;data.payments.add(payment);}})) {
                    hideKeyboard();projectId=p.id;route="project";render();toast(amount>0&&priceValue!=null&&amount>priceValue?"Projekt spremljen. Polog je veći od cijene.":"Projekt je spremljen.");
                }
            }catch(Exception e){formError(e);}
        }));
    }
    private void clientForm() {
        Client original=clientId==null?null:data.client(clientId);
        body.addView(text(original==null?"Dodajte novog klijenta.":original.name(),24,ink,true));gap(body,20);LinearLayout form=card();
        EditText first=field(form,"Ime *",original==null?"":original.firstName,"Ime",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText last=field(form,"Prezime *",original==null?"":original.lastName,"Prezime",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText email=field(form,"Email",original==null?"":original.email,"ime@primjer.hr",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText phone=field(form,"Telefon",original==null?"":original.phone,"+385",InputType.TYPE_CLASS_PHONE);
        EditText institution=field(form,"Institucija / fakultet",original==null?"":original.institution,"Npr. Medicinski fakultet",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        EditText notes=notes(form,original==null?"":original.notes);body.addView(form);gap(body,20);
        body.addView(button("Spremi klijenta",BLUE,Color.WHITE,()->{
            try {
                String f=required(first),l=required(last);Client c=original==null?new Client():Backup.readClient(Backup.clientJson(original));
                c.firstName=f;c.lastName=l;c.email=email.getText().toString().trim();c.phone=phone.getText().toString().trim();c.institution=institution.getText().toString().trim();c.notes=notes.getText().toString().trim();c.updatedAt=Domain.timestamp();
                if(commit(()->{if(original!=null)data.clients.remove(original);data.clients.add(c);})) {clientId=c.id;route="client";hideKeyboard();render();toast("Klijent je spremljen.");}
            }catch(Exception e){formError(e);}
        }));
    }
    private void paymentForm() {
        Project project=data.project(projectId);if(project==null){route="main";render();return;}
        Payment original=null;for(Payment p:data.payments)if(p.id.equals(paymentId))original=p;Payment existing=original;
        Client client=data.client(project.clientId);body.addView(text(client.name(),24,ink,true));gap(body,5);body.addView(text(Domain.title(data,project),13,muted,false));gap(body,18);
        LinearLayout form=card();value(form,"Trenutno preostalo",project.price==null?"Cijena nije definirana":Domain.money(Domain.remaining(data,project)));
        EditText amount=field(form,"Iznos uplate (€) *",existing==null?"":Domain.inputMoney(existing.amount),"0,00",InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText date=dateField(form,"Datum uplate *",existing==null?Domain.today().toString():existing.paymentDate);
        List<String> types=new ArrayList<>();types.add("Automatski prijedlog");for(PaymentType t:PaymentType.values())types.add(paymentTypeLabel(t));Spinner type=spinner(form,"Vrsta uplate",types,existing==null?0:existing.type.ordinal()+1);
        TextView hint=text("",12,muted,false);hint.setPadding(0,dp(6),0,dp(12));form.addView(hint);
        Consumer<String> update=value->{
            try {long n=Domain.parseMoney(value);Data without=new Data();without.payments.addAll(data.payments);if(existing!=null)without.payments.remove(existing);long after=Domain.paid(without,project)+n;
                boolean over=project.price!=null&&after>project.price;hint.setText(over?"Uplata prelazi cijenu. Preplata: +"+Domain.money(after-project.price):"Prijedlog: "+paymentTypeLabel(Domain.suggestedType(without,project,n)));hint.setTextColor(over?ORANGE:muted);
            }catch(RuntimeException e){hint.setText("Iznos mora biti veći od 0 €. Preplata je dopuštena.");hint.setTextColor(muted);}
        };watch(amount,update);update.accept(amount.getText().toString());
        EditText notes=notes(form,existing==null?"":existing.notes);body.addView(form);gap(body,20);
        body.addView(button("Spremi uplatu",BLUE,Color.WHITE,()->{
            try {long n=validMoney(amount,true);String day=validDate(date);Payment p=existing==null?new Payment():Backup.readPayment(Backup.paymentJson(existing));p.projectId=project.id;p.amount=n;p.paymentDate=day;p.notes=notes.getText().toString().trim();p.updatedAt=Domain.timestamp();
                Data without=new Data();without.payments.addAll(data.payments);if(existing!=null)without.payments.remove(existing);p.type=type.getSelectedItemPosition()==0?Domain.suggestedType(without,project,n):PaymentType.values()[type.getSelectedItemPosition()-1];
                if(commit(()->{if(existing!=null)data.payments.remove(existing);data.payments.add(p);})) {hideKeyboard();route="project";render();toast("Uplata je spremljena.");}
            }catch(Exception e){formError(e);}
        }));
        if(existing!=null){gap(body,12);body.addView(button("Obriši uplatu",tint(RED),RED,()->new AlertDialog.Builder(this).setTitle("Obrisati uplatu?").setMessage(Domain.money(existing.amount)+" · "+Domain.date(existing.paymentDate)+"\nFinancijski status projekta ponovno će se izračunati.").setNegativeButton("Odustani",null).setPositiveButton("Obriši",(d,w)->{if(commit(()->data.payments.remove(existing))){route="project";render();toast("Uplata je obrisana.");}}).show()));}
    }
    private void settings() {
        pageHeading("Postavke","Vaši podaci ostaju na ovom uređaju.");
        LinearLayout prefs=card();value(prefs,"Valuta","EUR (€)");value(prefs,"Jezik","Hrvatski");Switch dark=new Switch(this);dark.setText("Tamni način rada");dark.setTextSize(14);dark.setTextColor(ink);dark.setMinimumHeight(dp(50));dark.setChecked(data.settings.darkMode);dark.setOnCheckedChangeListener((v,on)->{if(commit(()->data.settings.darkMode=on))render();});prefs.addView(dark);body.addView(prefs);gap(body,22);
        body.addView(text("Sigurnosne kopije",20,ink,true));gap(body,8);body.addView(text("JSON kopija sadrži klijente, projekte, uplate i postavke. Spremite je na mjesto koje ste sami odabrali.",13,muted,false));gap(body,16);
        body.addView(button("Izvezi sigurnosnu kopiju",BLUE,Color.WHITE,()->{
            try{stageExport(Backup.encode(data));createDocument("application/json","projekt-naplata-"+Domain.today()+".json",101);}catch(Exception e){formError(e);}
        }));gap(body,10);body.addView(button("Vrati podatke iz kopije",surface,BLUE,()->{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");startActivityForResult(intent,102);
        }));gap(body,24);
        body.addView(text("Izvoz uplata",20,ink,true));gap(body,8);body.addView(text("CSV pregled za vlastitu analizu ili računovodstvo.",13,muted,false));gap(body,14);body.addView(button("Izvezi uplate (CSV)",surface,BLUE,()->{try{stageExport(paymentsCsv());createDocument("text/csv","uplate-"+Domain.today()+".csv",103);}catch(Exception e){formError(e);}}));gap(body,24);
        List<Project> deleted=data.projects.stream().filter(p->p.deletedAt!=null).collect(java.util.stream.Collectors.toList());
        if(!deleted.isEmpty()) {
            body.addView(text("Obrisani projekti",20,ink,true));gap(body,8);body.addView(text("Obrisani zapisi čuvaju se lokalno i nisu uključeni u analitiku.",12,muted,false));gap(body,12);
            for(Project p:deleted){LinearLayout card=card();card.addView(text(Domain.title(data,p),14,ink,true));gap(card,8);card.addView(button("Vrati projekt i njegove uplate",surface,BLUE,()->{if(commit(()->{p.deletedAt=null;p.updatedAt=Domain.timestamp();})){render();toast("Projekt je vraćen.");}}));body.addView(card);gap(body,10);}
        }
        LinearLayout privacy=card();privacy.addView(text("Lokalno. Privatno. Jednostavno.",15,ink,true));gap(privacy,8);privacy.addView(text("Aplikacija nema mrežni pristup. Automatsko Android sigurnosno kopiranje je isključeno. Za prijenos na drugi uređaj koristite JSON kopiju.",12,muted,false));gap(privacy,10);privacy.addView(text(getString(hr.statistika.tracker.R.string.app_version),11,muted,false));body.addView(privacy);
    }
    private void createDocument(String mime,String name,int request) {
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(mime);intent.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(intent,request);
    }
    private void stageExport(String payload) throws java.io.IOException {
        java.io.File file=java.io.File.createTempFile("tracker-export-",".tmp",getCacheDir());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(payload.getBytes(StandardCharsets.UTF_8));}
        pendingExport=file.getName();
    }
    @Override protected void onActivityResult(int request,int result,Intent intent) {
        super.onActivityResult(request,result,intent);
        if(result!=RESULT_OK||intent==null||intent.getData()==null){if((request==101||request==103)&&pendingExport!=null){new java.io.File(getCacheDir(),pendingExport).delete();pendingExport=null;}return;}
        android.net.Uri uri=intent.getData();
        if(request==101||request==103) {
            String filename=pendingExport;pendingExport=null;if(filename==null){toast("Ponovite izvoz sigurnosne kopije.");return;}
            java.io.File staged=new java.io.File(getCacheDir(),filename);
            toast("Spremanje datoteke…");
            files.execute(()->{try(InputStream in=new java.io.FileInputStream(staged);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IllegalStateException("Datoteka nije dostupna.");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();runOnUiThread(()->toast("Datoteka je spremljena."));}catch(Exception e){runOnUiThread(()->showError("Datoteka nije spremljena. "+e.getMessage()));}finally{staged.delete();}});
        } else if(request==102) {
            toast("Provjera sigurnosne kopije…");
            files.execute(()->{
                try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    if(in==null)throw new IllegalArgumentException("Datoteka nije dostupna.");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>10*1024*1024)throw new IllegalArgumentException("Najveća podržana veličina je 10 MB.");out.write(buffer,0,n);}
                    Data imported=Backup.decode(out.toString(StandardCharsets.UTF_8.name()));
                    runOnUiThread(()->{if(isDestroyed())return;new AlertDialog.Builder(this).setTitle("Vratiti sigurnosnu kopiju?").setMessage(imported.clients.size()+" klijenata · "+imported.visibleProjects().size()+" projekata · "+imported.payments.size()+" uplata\n\nOvo zamjenjuje sve trenutne podatke. Prije zamjene izvezite kopiju ako želite zadržati postojeće podatke.").setNegativeButton("Odustani",null).setPositiveButton("Zamijeni podatke",(d,w)->{try{store.save(imported);data=imported;route="main";tab="dashboard";projectQuery="";dashboardQuery="";clientQuery="";render();toast("Podaci su vraćeni.");}catch(Exception e){showError("Podaci nisu zamijenjeni. "+e.getMessage());}}).show();});
                }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())showError("Kopija nije uvezena. Postojeći podaci su sačuvani.\n"+e.getMessage());});}
            });
        }
    }
    private String paymentsCsv() {
        StringBuilder csv=new StringBuilder("\ufeffDatum;Klijent;Projekt;Vrsta uplate;Iznos (€)\r\n");List<Payment> payments=new ArrayList<>(data.payments);payments.sort(Comparator.comparing(p->p.paymentDate));
        for(Payment payment:payments){Project p=data.project(payment.projectId);if(p==null||p.deletedAt!=null)continue;Client c=data.client(p.clientId);csv.append(csvCell(Domain.date(payment.paymentDate))).append(';').append(csvCell(c.name())).append(';').append(csvCell(Domain.title(data,p))).append(';').append(csvCell(paymentTypeLabel(payment.type))).append(';').append(csvCell(Domain.inputMoney(payment.amount))).append("\r\n");}
        return csv.toString();
    }
    private String csvCell(String value) {String safe=value;if(!safe.isEmpty()&&"=+-@\t\r\n".indexOf(safe.charAt(0))>=0)safe="'"+safe;return "\""+safe.replace("\"","\"\"")+"\"";}
    private void openProject(Project p) {hideKeyboard();if(route.equals("client"))returnClientId=clientId;projectId=p.id;route="project";render();}
    private void openProjectForm(Project p) {hideKeyboard();projectId=p==null?null:p.id;editorClientId=p==null?null:p.clientId;route="projectForm";render();}
    private void openPaymentForm(Project p,Payment payment) {hideKeyboard();projectId=p.id;paymentId=payment==null?null:payment.id;route="paymentForm";render();}
    private void setStatus(Project p,ProjectStatus status) {if(commit(()->Domain.changeStatus(p,status,Domain.today()))){render();toast("Status: "+statusLabel(status));}}
    private void quickPay(Project p) {
        long amount=Domain.remaining(data,p);if(p.price==null||amount<=0)return;
        Payment payment=new Payment();payment.projectId=p.id;payment.amount=amount;payment.type=PaymentType.FINAL;
        if(commit(()->data.payments.add(payment))){render();toast("Evidentirana završna uplata: "+Domain.money(amount));}
    }
    private void confirmDelete(Project p) {
        int count=data.paymentsFor(p.id).size();new AlertDialog.Builder(this).setTitle("Obrisati projekt?").setMessage(Domain.title(data,p)+(count>0?"\n\nObrisat će se i "+count+" povezanih uplata. One više neće biti uključene u naplatu i analitiku.":"")+"\n\nProjekt možete vratiti iz Postavki.").setNegativeButton("Odustani",null).setPositiveButton("Obriši",(d,w)->{if(commit(()->{p.deletedAt=Domain.timestamp();p.updatedAt=p.deletedAt;})){route="main";render();toast("Projekt je obrisan.");}}).show();
    }
    private boolean commit(Runnable mutation) {
        try{mutation.run();store.save(data);return true;}catch(Exception e){try{data=store.load();}catch(Exception ignored){}showError("Promjena nije spremljena. "+e.getMessage());return false;}
    }
    @Override public void onBackPressed() {
        if(route.endsWith("Form"))new AlertDialog.Builder(this).setTitle("Odustati od unosa?").setMessage("Nespremljene promjene bit će izgubljene.").setNegativeButton("Nastavi uređivati",null).setPositiveButton("Odustani",(d,w)->goBack()).show();else goBack();
    }
    private void goBack() {
        hideKeyboard();switch(route){case "paymentForm":route="project";break;case "projectForm":route=projectId==null?"main":"project";if(projectId==null&&returnClientId!=null){clientId=returnClientId;route="client";}break;case "clientForm":route=clientId==null?"main":"client";break;
            case "project":if(returnClientId!=null){clientId=returnClientId;returnClientId=null;route="client";}else route="main";break;case "client":route="main";tab="clients";break;default:if(!tab.equals("dashboard")){tab="dashboard";}else{super.onBackPressed();return;}}
        render();
    }
    private String statusLabel(ProjectStatus s) {switch(s){case TODO:return "Za započeti";case IN_PROGRESS:return "U tijeku";case DONE:return "Završeno";default:return "Arhivirano";}}
    private int statusColor(ProjectStatus s) {return s==ProjectStatus.IN_PROGRESS?BLUE:s==ProjectStatus.DONE?GREEN:muted;}
    private String paymentLabel(PaymentStatus s) {switch(s){case PAID:return "Plaćeno";case PARTIALLY_PAID:return "Djelomično";case NOT_PAID:return "Neplaćeno";default:return "Bez cijene";}}
    private int paymentColor(PaymentStatus s) {return s==PaymentStatus.PAID?GREEN:s==PaymentStatus.PARTIALLY_PAID?ORANGE:s==PaymentStatus.NOT_PAID?RED:muted;}
    private String paymentTypeLabel(PaymentType s) {switch(s){case DEPOSIT:return "Polog";case PARTIAL:return "Djelomična uplata";case FINAL:return "Završna uplata";default:return "Ostalo";}}
    private void pageHeading(String title,String subtitle){body.addView(text(title,28,ink,true));gap(body,6);body.addView(text(subtitle,13,muted,false));gap(body,20);}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);return layout;}
    private void gap(LinearLayout parent,int size){View space=new View(this);parent.addView(space,new LinearLayout.LayoutParams(1,dp(size)));}
    private LinearLayout.LayoutParams weighted(int right){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.rightMargin=dp(right);return p;}
    private TextView text(String value,int size,int color,boolean bold){TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setFontFeatureSettings("tnum");view.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));view.setIncludeFontPadding(false);view.setLineSpacing(dp(3),1);return view;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable bordered(int color,int radius,int border){GradientDrawable d=shape(color,radius);d.setStroke(dp(1),border);return d;}
    private int tint(int color){if(data.settings.darkMode)return Color.rgb((Color.red(color)+Color.red(surface)*4)/5,(Color.green(color)+Color.green(surface)*4)/5,(Color.blue(color)+Color.blue(surface)*4)/5);return Color.rgb((Color.red(color)+255*9)/10,(Color.green(color)+255*9)/10,(Color.blue(color)+255*9)/10);}
    private LinearLayout card(){LinearLayout card=column();card.setPadding(dp(16),dp(16),dp(16),dp(16));card.setBackground(bordered(surface,16,line));return card;}
    private TextView badge(String value,int color){TextView b=text(value,10,color,true);b.setPadding(dp(8),dp(5),dp(8),dp(5));b.setBackground(shape(tint(color),8));return b;}
    private TextView button(String value,int background,int color,Runnable action){TextView b=text(value,14,color,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(14),dp(14),dp(14),dp(14));b.setMinimumHeight(dp(48));b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22315fe8),bordered(background,12,background==surface?line:background),null));b.setOnClickListener(v->action.run());b.setFocusable(true);return b;}
    private TextView smallButton(String value,Runnable action){TextView b=button(value,tint(BLUE),BLUE,action);b.setTextSize(12);b.setPadding(dp(12),dp(10),dp(12),dp(10));return b;}
    private View iconButton(String icon,String description,Runnable action){FrameLayout box=new FrameLayout(this);box.setContentDescription(description);box.setBackground(shape(surface,12));box.setMinimumHeight(dp(44));box.setMinimumWidth(dp(44));IconView v=new IconView(this,icon,ink);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);box.addView(v,p);box.setOnClickListener(view->action.run());return box;}
    private void empty(LinearLayout parent,String title,String subtitle,String icon){LinearLayout card=card();card.setGravity(Gravity.CENTER);IconView image=new IconView(this,icon,muted);card.addView(image,new LinearLayout.LayoutParams(dp(26),dp(26)));gap(card,12);TextView t=text(title,14,ink,true);t.setGravity(Gravity.CENTER);card.addView(t);gap(card,6);TextView s=text(subtitle,11,muted,false);s.setGravity(Gravity.CENTER);card.addView(s);parent.addView(card);}
    private void value(LinearLayout parent,String title,String value){LinearLayout row=row();row.setGravity(Gravity.TOP);row.setPadding(0,dp(8),0,dp(8));row.addView(text(title,12,muted,false),new LinearLayout.LayoutParams(0,-2,1));TextView t=text(value,13,ink,true);t.setGravity(Gravity.END);t.setTextIsSelectable(true);row.addView(t,new LinearLayout.LayoutParams(0,-2,1.3f));parent.addView(row);}
    private void label(LinearLayout parent,String value){TextView label=text(value,12,ink,true);label.setPadding(0,0,0,dp(7));parent.addView(label);}
    private void styleInput(EditText view,String hint){view.setTextSize(14);view.setTextColor(ink);view.setHintTextColor(muted);view.setHint(hint);view.setPadding(dp(12),dp(10),dp(12),dp(10));view.setBackground(bordered(bg,10,line));view.setSingleLine(true);view.setSelectAllOnFocus(false);}
    private EditText field(LinearLayout parent,String label,String value,String hint,int type){label(parent,label);EditText input=new EditText(this);styleInput(input,hint);input.setInputType(type);input.setText(value);input.setContentDescription(label);fields.put(label,input);parent.addView(input,new LinearLayout.LayoutParams(-1,dp(50)));gap(parent,16);return input;}
    private EditText notes(LinearLayout parent,String value){EditText input=field(parent,"Napomena",value,"Dogovori, detalji analize, podsjetnici…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setSingleLine(false);input.setMinLines(3);input.setGravity(Gravity.TOP);input.getLayoutParams().height=dp(104);return input;}
    private EditText dateField(LinearLayout parent,String label,String iso){label(parent,label);LinearLayout row=row();EditText input=new EditText(this);styleInput(input,"DD.MM.GGGG.");input.setInputType(InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE);input.setText(Domain.date(iso));input.setContentDescription(label);fields.put(label,input);row.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));
        View calendar=iconButton("clock","Odaberi "+label.toLowerCase(Locale.ROOT),()->{LocalDate day;try{day=LocalDate.parse(Domain.parseDate(input.getText().toString()));}catch(Exception e){day=Domain.today();}DatePickerDialog picker=new DatePickerDialog(this,(v,y,m,d)->input.setText(Domain.date(LocalDate.of(y,m+1,d).toString())),day.getYear(),day.getMonthValue()-1,day.getDayOfMonth());picker.show();});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(48),dp(50));p.leftMargin=dp(6);row.addView(calendar,p);parent.addView(row);gap(parent,16);return input;}
    private Spinner spinner(LinearLayout parent,String label,List<String> options,int selected){label(parent,label);Spinner spinner=new Spinner(this);spinner.setAdapter(adapter(options));spinner.setSelection(selected);spinner.setBackground(bordered(bg,10,line));spinner.setPadding(dp(8),0,dp(8),0);spinner.setContentDescription(label);fields.put(label,spinner);parent.addView(spinner,new LinearLayout.LayoutParams(-1,dp(50)));gap(parent,16);return spinner;}
    private ArrayAdapter<String> adapter(List<String> values){return new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){@Override public View getView(int position,View convertView,ViewGroup parent){TextView v=(TextView)super.getView(position,convertView,parent);v.setTextColor(ink);v.setTextSize(14);return v;}@Override public View getDropDownView(int position,View convertView,ViewGroup parent){TextView v=(TextView)super.getDropDownView(position,convertView,parent);v.setTextColor(ink);v.setBackgroundColor(surface);v.setMinimumHeight(dp(48));return v;}};}
    private View search(String hint,String value,Consumer<String> listener){LinearLayout box=row();box.setGravity(Gravity.CENTER_VERTICAL);box.setBackground(bordered(surface,12,line));box.setPadding(dp(12),0,dp(8),0);box.addView(new IconView(this,"search",muted),new LinearLayout.LayoutParams(dp(19),dp(19)));EditText input=new EditText(this);styleInput(input,hint);input.setBackgroundColor(Color.TRANSPARENT);input.setInputType(InputType.TYPE_CLASS_TEXT);input.setText(value);input.setContentDescription(hint);box.addView(input,new LinearLayout.LayoutParams(0,dp(50),1));watch(input,listener);return box;}
    private void watch(EditText input,Consumer<String> listener){input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){listener.accept(s.toString());}public void afterTextChanged(Editable e){}});}
    private String required(EditText field){String value=field.getText().toString().trim();if(value.trim().isEmpty()){field.setError("Obavezno polje");field.requestFocus();throw new IllegalArgumentException("Unesite ime i prezime klijenta.");}return value;}
    private String validDate(EditText field){try{return Domain.parseDate(field.getText().toString());}catch(RuntimeException e){field.setError(e.getMessage());field.requestFocus();throw e;}}
    private long validMoney(EditText field,boolean positive){try{long amount=Domain.parseMoney(field.getText().toString());if(positive&&amount==0)throw new IllegalArgumentException("Iznos uplate mora biti veći od 0 €.");return amount;}catch(RuntimeException e){field.setError(e.getMessage());field.requestFocus();throw e;}}
    private void formError(Exception e){if(e instanceof IllegalArgumentException)toast(e.getMessage());else showError("Unos nije spremljen. "+e.getMessage());}
    private void toast(String message){if(!isDestroyed())Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void showError(String message){if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Provjerite podatke").setMessage(message).setPositiveButton("U redu",null).show();}
    private void hideKeyboard(){View focus=getCurrentFocus();if(focus!=null){((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focus.getWindowToken(),0);focus.clearFocus();}}
}
