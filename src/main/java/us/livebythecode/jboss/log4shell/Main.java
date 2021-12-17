package us.livebythecode.jboss.log4shell;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.xml.XMLConstants;
import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException; 

   
public class Main {

   private static String FILENAME = "";
   private static String CONTENTPATH = "";
   private String currentEARName = "";
   
   public static void main(String[] args) {
      if(args.length == 2) {
         Main main = new Main();
         FILENAME=args[0];
         CONTENTPATH=args[1];
         main.start();
      }else {
         System.out.println("Wrong args: domain.xml, content path");
      }
   }

   public void start() {
      try {
         HashMap<String, String> deployments = parseDomainXML();
         HashMap<String, String> newOldHashMap = new HashMap<String, String>();
         processDeployments(deployments,newOldHashMap);
         generateXML(deployments,newOldHashMap);
         renameContent(deployments,newOldHashMap);
         renameDirectories(deployments,newOldHashMap);
      }catch (IOException e) {
         e.printStackTrace();
      }catch (ParserConfigurationException e) {
         e.printStackTrace();
      }catch (SAXException e) {
         e.printStackTrace();
      }catch (Exception e) {
         e.printStackTrace();
      }

   }
   

   private void generateXML(HashMap<String, String> deployments,HashMap<String, String> newOldHashMap) {
      System.out.println("_______________________________________________________");
      System.out.println("");
      System.out.println("");
      System.out.println("Replace the <deployments> section of domain.xml with the following:");
      System.out.println("");
      System.out.println("");
      for (String i : deployments.keySet()) {
         System.out.println("<deployment name=\""+deployments.get(i)+"\" runtime-name=\""+deployments.get(i)+"\">");
         System.out.println("<content sha1=\""+newOldHashMap.get(i)+"\"/>");
         System.out.println("</deployment>");
      }
   }
   
   private HashMap<String, String> parseDomainXML() throws  IOException, ParserConfigurationException, SAXException {
      System.out.println("Parsing "+FILENAME);
      HashMap<String, String> deployments = new HashMap<String, String>();
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      Document doc = dbf.newDocumentBuilder().parse(new File(FILENAME));
      doc.getDocumentElement().normalize();
      NodeList list = doc.getElementsByTagName("deployments");
      if(list.getLength()>0) {
         Node deploymentsNode = list.item(0);
         NodeList deploymentNodeList = deploymentsNode.getChildNodes();
         for (int temp = 0; temp < deploymentNodeList.getLength(); temp++) {
            Node node = deploymentNodeList.item(temp);
            if(null!=node.getAttributes()) {
               NodeList contentNodeList = node.getChildNodes();
               for (int temp1 = 0; temp1 < contentNodeList.getLength(); temp1++) {
                  Node contentNode = contentNodeList.item(temp1);
                  if(null!=contentNode.getAttributes()) {
                     deployments.put(contentNode.getAttributes().getNamedItem("sha1").getNodeValue(), node.getAttributes().getNamedItem("name").getNodeValue());
                  }
               }

            }
         }
      }else {
         System.out.println("Unexpected deployments node size "+list.getLength());
      }
      return deployments;
   }
   
   private void processDeployments(HashMap<String, String> deployments,HashMap<String, String> newOldHashMap) throws Exception {
      for (String i : deployments.keySet()) {
         String dir1 = i.substring(0,2);
         String dir2 = i.substring(2,i.length());
         System.out.println("Processing: " + deployments.get(i)+ " ("+CONTENTPATH+"/"+dir1+"/"+dir2+"/content )");
         ZipInputStream earFileIn = new ZipInputStream(new FileInputStream(CONTENTPATH+"/"+dir1+"/"+dir2+"/content"));
         ZipOutputStream earFileOut = new ZipOutputStream(new FileOutputStream(CONTENTPATH+"/"+dir1+"/"+dir2+"/content2"));
         byte[] buf = new byte[1024];
         ZipEntry earEntry;
         while((earEntry = earFileIn.getNextEntry()) != null){
             if(!earEntry.getName().contains(".war") && !earEntry.getName().contains("log4j-core")){
                earFileOut.putNextEntry(new ZipEntry(earEntry.getName()));
                 int len;
                 while ((len = earFileIn.read(buf)) > 0) {
                    earFileOut.write(buf, 0, len);
                 }
                 earFileIn.closeEntry();
                 earFileOut.closeEntry();
             }else if(earEntry.getName().contains(".war")){
                ZipInputStream warInputStream = convertToZipInputStream(earFileIn);
                ByteArrayOutputStream warBAOS = processWar(warInputStream, earEntry.getName());
                byte[] warBytes = warBAOS.toByteArray();
                ByteArrayInputStream warBIS = new ByteArrayInputStream(warBytes);
                earFileOut.putNextEntry(new ZipEntry(earEntry.getName()));
                int len;
                while ((len = warBIS.read(buf)) > 0) {
                   earFileOut.write(buf, 0, len);
                }
               earFileOut.closeEntry();
             }else if(earEntry.getName().contains("log4j-core")){
                System.out.println("Found log4j: "+earEntry.getName() +" App: "+currentEARName);
                ZipInputStream jarInputStream = convertToZipInputStream(earFileIn);
                ByteArrayOutputStream jarBAOS = processJar(jarInputStream,earEntry.getName());
                byte[] jarBytes = jarBAOS.toByteArray();
                ByteArrayInputStream jarBIS = new ByteArrayInputStream(jarBytes);
                earFileOut.putNextEntry(new ZipEntry(earEntry.getName()));
                int len;
                while ((len = jarBIS.read(buf)) > 0) {
                   earFileOut.write(buf, 0, len);
                }
                earFileOut.closeEntry();
             }
         }
         earFileOut.close();
         earFileIn.close();
         String sha1 = createSha1(new File(CONTENTPATH+"/"+dir1+"/"+dir2+"/content2"));
         newOldHashMap.put(i, sha1);
         System.out.println("Sha1="+sha1);
      System.out.println("__________________________________________________________");
      }
   }
   
   private ZipInputStream convertToZipInputStream(final ZipInputStream inputStreamIn) throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      IOUtils.copy(inputStreamIn, out);
      return new ZipInputStream(new ByteArrayInputStream(out.toByteArray()));
   }
   
   public String createSha1(File file) throws Exception  {
      System.out.println("Creating sha1");
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      InputStream fis = new FileInputStream(file);
      int n = 0;
      byte[] buffer = new byte[8192];
      while (n != -1) {
          n = fis.read(buffer);
          if (n > 0) {
              digest.update(buffer, 0, n);
          }
      }
      fis.close();
      return new HexBinaryAdapter().marshal(digest.digest()).toLowerCase();
   }
   
   private ByteArrayOutputStream processWar(ZipInputStream warInputStream, String warName) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ZipOutputStream warOutputStream = new ZipOutputStream(baos);
      try {
         byte[] buf = new byte[1024];
         ZipEntry warEntry;
         while((warEntry = warInputStream.getNextEntry()) != null){
            System.out.print("1");
            if(!warEntry.getName().contains("log4j-core")){
               warOutputStream.putNextEntry(new ZipEntry(warEntry.getName()));
                int len;
                while ((len = warInputStream.read(buf)) > 0) {
                   warOutputStream.write(buf, 0, len);
                }
                warInputStream.closeEntry();
                warOutputStream.closeEntry();
            }else{
               System.out.println("Found log4j: "+warEntry.getName() +" App: "+currentEARName);
               ZipInputStream jarInputStream = convertToZipInputStream(warInputStream);
               ByteArrayOutputStream jarBAOS = processJar(jarInputStream,warEntry.getName());
               byte[] jarBytes = jarBAOS.toByteArray();
               ByteArrayInputStream jarBIS = new ByteArrayInputStream(jarBytes);
               warOutputStream.putNextEntry(new ZipEntry(warEntry.getName()));
               int len;
               while ((len = jarBIS.read(buf)) > 0) {
                  warOutputStream.write(buf, 0, len);
               }
               warOutputStream.closeEntry();
            }
         }
      }catch(IOException ioe) {
         ioe.printStackTrace();
      }
      warOutputStream.close();
      return baos;
   
   }
   
   private ByteArrayOutputStream processJar(ZipInputStream jarInputStream, String jarName) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ZipOutputStream jarOutputStream = new ZipOutputStream(baos);
      try {
         byte[] buf = new byte[1024];
         ZipEntry jarEntry;
         while((jarEntry = jarInputStream.getNextEntry()) != null){
            if(!jarEntry.getName().contains("JndiLookup")){
               jarOutputStream.putNextEntry(new ZipEntry(jarEntry.getName()));
                int len;
                while ((len = jarInputStream.read(buf)) > 0) {
                   jarOutputStream.write(buf, 0, len);
                }
                jarInputStream.closeEntry();
                jarOutputStream.closeEntry();
            }else{
               System.out.println("Deleting JndiLookup Class: "+jarEntry.getName());
            }
         }
      }catch(IOException ioe) {
         ioe.printStackTrace();
      }
      jarOutputStream.close();
      return baos;

   }
   
   private void renameContent(HashMap<String, String> deployments,HashMap<String, String> newOldHashMap)  {
      for (String i : deployments.keySet()) {
         String oldDir1 = i.substring(0,2);
         String oldDir2 = i.substring(2,i.length());
         try {
            Files.deleteIfExists(Paths.get(CONTENTPATH+"/"+oldDir1+"/"+oldDir2+"/content"));
         }
         catch (IOException e) {
            System.out.println("Failed to delete file "+CONTENTPATH+"/"+oldDir1+"/"+oldDir2+"/content");
         }
         renameFile(CONTENTPATH+"/"+oldDir1+"/"+oldDir2+"/content2",CONTENTPATH+"/"+oldDir1+"/"+oldDir2+"/content" );
      }
   }
   
   private boolean renameFile(String oldName, String newName) {
      return new File(oldName).renameTo(new File(newName));
   }

   private void renameDirectories(HashMap<String, String> deployments,HashMap<String, String> newOldHashMap) {
      for (String i : deployments.keySet()) {
         renameDirectory(CONTENTPATH+"/"+i.substring(0,2)+"/"+i.substring(2,i.length()), newOldHashMap.get(i).substring(2,i.length()));
      }
      for (String i : deployments.keySet()) {
         renameDirectory(CONTENTPATH+"/"+i.substring(0,2), newOldHashMap.get(i).substring(2,i.length()));
      }
   }
   
   private void renameDirectory(String dirPath, String newDirName) {
      File dir = new File(dirPath);
      if (!dir.isDirectory()) {
        System.err.println("There is no directory @ given path: "+dirPath);
      } else {
          dir.renameTo(new File(dir.getParent() + "/" + newDirName));
      }
   }

}