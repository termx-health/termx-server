package com.kodality.termserver.integration.icd10est;

public class Icd10EstConfiguration {
  public static final String codeSystem = "icd-10-est";
  public static final String version = "8";
  public static final String uri = "https://pub.e-tervis.ee/classifications/RHK-10/8";
  public static final String source = "Ministry of Social Affairs of Estonia";
  public static final String codeSystemDescription = "RHK-10 on rahvusvaheline haiguste ja nendega seotud terviseprobleemide statistiline klassifikatsioon, mille sisu haldaja on Sotsiaalministeerium. Täiendus- ja muudatusettepanekud edastada info@sm.ee.";
  public static final String codeSystemVersionDescription = "22.04.2021 on 8. versiooni lisatud järgmised uued koodid:\n" +
      "U08 COVID-19 anamneesis\n" +
      "U08.9 COVID-19 anamneesis, täpsustamata\n" +
      "U09 COVID-19 järgne seisund\n" +
      "U09.9 COVID-19 järgne seisund, täpsustamata\n" +
      "U10 COVID-19 tekkeline süsteemne põletikuline sündroom\n" +
      "U10.9 COVID-19 tekkeline süsteemne põletikuline sündroom, täpsustamata\n" +
      "U11 Immuniseerimise vajadus COVID-19 vastu\n" +
      "U11.9 Immuniseerimise vajadus COVID-19 vastu, täpsustamata\n" +
      "U12 COVID-19 vaktsiini põhjustatud soovimatu mõju\n" +
      "U12.9 COVID-19 vaktsiini põhjustatud soovimatu mõju, täpsustamata\n" +
      "\n" +
      "Parandati järgmiste koodide nimetusi:\n" +
      "D69.3 Idiopaatiline mittetrombotsütopeeniline purpur -> Idiopaatiline trombotsütopeeniline purpur\n" +
      "C79.0 Neeru ja neeruvagna metastaatiline pahaloomuline kasvaja -> Neeru ja neeruvaagna metastaatiline pahaloomuline kasvaja\n" +
      "S06.7 Kolijusisene vigastus pikema kooma [meelemarkusetusega] -> Koljusisene vigastus pikema kooma [meelemärkusetusega]\n" +
      "\n" +
      "Lisaks on eelmise (7.) versiooni järgselt arendajatelt laekunud tagasiside alusel parandatud XMLi struktuuri järgmistes failides A00-B99.xml, C00-D48.xml, E00-E90.xml, F00-F99.xml, N00-N99.xml, P00-P99.xml, S00-T98.xml, V01-Y99.xml. Antud parandused ei ole seotud koodide sisuliste muudatustega, vaid mõjutasid XMLide importi.\n" +
      "\n" +
      "Muudatus- ja täiendusettepanekud edastada klassifikaatori sisu haldajale, Sotsiaalministeeriumile aadressil info@sm.ee.Publitseeritud failidega seotud küsimused edastage palun andmekorraldus@tehik.ee.";
}
