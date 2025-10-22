package de.openfabtwin.bimserver.checkingservice.report;


public class Reporter {
    private final Results results;

    public Reporter(Results results) {
        this.results = results;
    }

    public String txtReport() {
        StringBuilder txt = new StringBuilder();
        txt.append("IDS Report\n");
        txt.append(results.getTitle()).append("\n");
        txt.append("==========\n\n");






        return txt.toString();
    }

//    public StringBuilder generateReport() {
//        txt.append("IDS Report\n");
//        txt.append(ids.getInfo().get("filename")).append("\n");
//        txt.append("==========\n\n");
//
//        txt.append("Info:\n");
//        ids.getInfo().forEach((k, v) -> {
//            if (!"filename".equalsIgnoreCase(k)) {   // skip filename
//                String value = (v == null) ? "-" : v.toString();
//                txt.append(" - ").append(k).append(": ").append(value).append("\n");
//            }
//        });
//        txt.append("\n");
//        txt.append("--------------------------------------\n");
//        txt.append("\n");
//
//        txt.append("Specifications:\n");
//        txt.append("\n");
//
//        return txt;
//    }

}
