package entsdksamples.soi;
/*
email: amir@wheretech.it
*/
import java.io.*;
import java.awt.*;
import java.util.Map;
import java.util.List;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import com.esri.arcgis.carto.*;
import com.esri.arcgis.system.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import com.esri.arcgis.geodatabase.*;
import org.apache.http.NameValuePair;
import com.esri.arcgis.server.SOIHelper;
import com.esri.arcgis.geometry.IGeometry;
import com.esri.arcgis.server.IServerObject;
import com.esri.arcgis.system.ServerUtilities;
import com.esri.arcgis.system.IServerUserInfo;
import com.esri.arcgis.server.IServerObjectHelper;
import com.esri.arcgis.interop.AutomationException;
import com.esri.arcgis.interop.extn.ArcGISExtension;
import com.esri.arcgis.server.IServerObjectExtension;
import com.esri.arcgis.geometry.JSONConverterGeometry;
import com.esri.arcgis.interop.extn.ServerObjectExtProperties;

@ArcGISExtension
@ServerObjectExtProperties(
        displayName = "Info_SOI",
        description = "Filter Map Service based on the groups of the user",
        interceptor = true,
        servicetype = "MapService",
        properties = "",
        supportsSharedInstances = true)

public class Info_SOI
        implements IServerObjectExtension, IRESTRequestHandler, IWebRequestHandler, IRequestHandler2, IRequestHandler {

    private ILog serverLog;
    private IServerObject so;
    private SOIHelper soiHelper;
    private IMapServer mapService;
    private IServerObjectHelper soHelper;
    private static Integer layer_counts, field_counts, map_counts;
    private static final long serialVersionUID = 1L;
    private static final String ARCGISHOME_ENV = "AGSSERVER";
    private static String user, default_map_name;
    private IServerUserInfo userinfo;
    private static final String WATERMARK_STRING = "© WhereTech S.r.l";
    /**
     * Default constructor.
     *
     * @throws Exception
     */
    public Info_SOI() throws Exception {
        super();
    }

    private static String getQuery(List<NameValuePair> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (NameValuePair pair : params) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(pair.getName(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(pair.getValue(), "UTF-8"));
        }
        return result.toString();
    }

    /**
     * init() is called once, when the instance of the SOE/SOI is created.
     *
     * @param soh the IServerObjectHelper
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    public void init(IServerObjectHelper soh) throws IOException, AutomationException {

        this.serverLog = ServerUtilities.getServerLogger();
        // Log message with server
        this.serverLog.addMessage(3, 200, "Initialized " + this.getClass().getName() + " SOI.");
        this.so = soh.getServerObject();
        String arcgisHome = getArcGISHomeDir();

        /* If null, throw an exception */
        if (arcgisHome == null) {
            serverLog.addMessage(1, 200, "Could not get ArcGIS home directory. Check if environment variable "
                    + ARCGISHOME_ENV + " is set.");
            throw new IOException("Could not get ArcGIS home directory. Check if environment variable " + ARCGISHOME_ENV
                    + " is set.");
        }
        if (!arcgisHome.endsWith(File.separator))
            arcgisHome += File.separator;
        // Load the SOI helper.
        this.soiHelper = new SOIHelper(arcgisHome + "XmlSchema" + File.separator + "MapServer.wsdl");
        this.soHelper = soh;
    }

    /**
     * This method is called to handle REST requests.
     *
     * @param capabilities       the capabilities
     * @param resourceName       the resource name
     * @param operationName      the operation name
     * @param operationInput     the operation input
     * @param outputFormat       the output format
     * @param requestProperties  the request properties
     * @param responseProperties the response properties
     * @return the response as byte[]
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    @Override
    public byte[] handleRESTRequest(String capabilities, String resourceName, String operationName,
                                    String operationInput, String outputFormat, String requestProperties, String[] responseProperties)
            throws IOException, AutomationException {

        // Print the ArcGIS Home
        serverLog.addMessage(3, 200, "Directory of ArcGIS Server is " + getArcGISHomeDir());

        // Find the correct delegate to forward the request too
        IRESTRequestHandler restRequestHandler = soiHelper.findRestRequestHandlerDelegate(so);
        byte[] response =
                restRequestHandler.handleRESTRequest(capabilities, resourceName, operationName, operationInput, outputFormat,
                        requestProperties, responseProperties);

        if (restRequestHandler != null) {

            serverLog.addMessage(4, 200, "restRequestHandler is not null");
            BufferedImage sourceImage = null;
            sourceImage = byteArrayToBufferedImage(response);
            JSONObject jsonObject = new JSONObject(operationInput);
            String image_format = jsonObject.getString("format");
            byte[] watermarkedImage =
                    addTextWatermark(WATERMARK_STRING, sourceImage, image_format,
                            outputFormat, null);

            if (watermarkedImage != null) {
                return watermarkedImage;
            }

            IMapServer ms = (IMapServer) this.soHelper.getServerObject();
            map_counts = ms.getMapCount();
            serverLog.addMessage(4, 200, "There is " + map_counts + " in this map service");
            for (int a = 0; a < map_counts; a++) {
                serverLog.addMessage(4, 200, "Name of the map " + a + " is " + ms.getMapName(a));
            }
            default_map_name = ms.getDefaultMapName();
            serverLog.addMessage(4, 200, "Default name of the map service is " + default_map_name);
            IMapServerInfo ms_info = ms.getServerInfo(default_map_name);
            IMapLayerInfos layer_info = ms_info.getMapLayerInfos();

            layer_counts = layer_info.getCount();
            for (int b = 0; b < layer_counts; b++) {
                serverLog.addMessage(4, 200, "Name of the layer " + b + " is " + layer_info.getElement(b).getName());
                serverLog.addMessage(4, 200, "Type of the layer " + b + " is " + layer_info.getElement(b).getType());
                serverLog.addMessage(4, 200, "ParentLayerID of the layer " + b + " is " + layer_info.getElement(b).getParentLayerID());
                serverLog.addMessage(4, 200, "ID of the layer " + b + " is " + layer_info.getElement(b).getID());
            }

            for (int c = 0; c < layer_counts; c++) {
                field_counts = layer_info.getElement(c).getFields().getFieldCount();
                for (int d = 0; d < field_counts; d++) {
                    serverLog.addMessage(4, 200, "Field " + d + " name is " + layer_info.getElement(c).getFields().getField(d).getName());
                    serverLog.addMessage(4, 200, "Field " + d + " type is " + layer_info.getElement(c).getFields().getField(d).getType());
                    serverLog.addMessage(4, 200, "Field " + d + " GeometryDef is " + layer_info.getElement(c).getFields().getField(d).getGeometryDef());
                    if (layer_info.getElement(c).getFields().getField(d).getGeometryDef() != null) {
                        serverLog.addMessage(4, 200, "Field " + d + " GeometryType is " + layer_info.getElement(c).getFields().getField(d).getGeometryDef().getGeometryType());
                    }
                }
            }

            // Get the User's info
            try {
                userinfo = ServerUtilities.getServerUserInfo();
                user = userinfo.getName();
                serverLog.addMessage(3, 200, "The username:  " + user);
            } catch (Exception e) {
                serverLog.addMessage(1, 200, "There is an error for getting the user " + e);
            }

            // Take the roles and groups of the User
            ArrayList<String> groupList = new ArrayList<>();
            try {
                int iteration = ServerUtilities.getGroupInfo().size();
                var roles = ServerUtilities.getServerUserInfo().getRoles();
                var groups = ServerUtilities.getGroupInfo();
                serverLog.addMessage(5, 200, "User:  " + user + " is the member of " + iteration + " groups");

                for (int d = 0; d <= iteration; d++) {
                    groupList.add(roles.next());
                }
            } catch (Exception e) {
                serverLog.addMessage(1, 200, "There is an error for getting the group list: " + e);
            }
            ILayerDescriptions layerDescs = ms_info.getDefaultMapDescription().getLayerDescriptions();
            for (int e = 0; e < layer_counts; e++) {
                ILayerDescription layerDesc = layerDescs.getElement(e);
                LayerResultOptions layerResOpt = new LayerResultOptions();
                layerResOpt.setIncludeGeometry(true);
                layerResOpt.setReturnFieldNamesInResults(true);
                layerDesc.setLayerResultOptionsByRef(layerResOpt);
                IMapTableDescription tableDesc = (IMapTableDescription) layerDesc;
                IQueryResultOptions resultOptions = new QueryResultOptions();
                resultOptions.setFormat(esriQueryResultFormat.esriQueryResultRecordSetAsObject);
                IQueryFilter filter = new QueryFilter();
                filter.setWhereClause("1 = 1");
                filter.addField("*");
                IQueryResult qry = ms.queryData(ms.getDefaultMapName(), tableDesc, filter, resultOptions);
                RecordSet rs = (RecordSet) qry.getObject();
                ICursor rc = rs.getCursor(false);
                IRow resultFeature;
                while ((resultFeature = rc.nextRow()) != null) {
                    Integer rs_field_counts = resultFeature.getFields().getFieldCount();
                    for (int f = 0; f < rs_field_counts; f++) {
                        serverLog.addMessage(5, 200, resultFeature.getFields().getField(f).getName() + " : " + resultFeature.getValue(f));
                        if (resultFeature.getFields().getField(f).getGeometryDef() != null) {
                            serverLog.addMessage(5, 200, "** getGeometryDef " + resultFeature.getFields().getField(f).getGeometryDef());
                            serverLog.addMessage(5, 200, "** getGeometryType " + resultFeature.getFields().getField(f).getGeometryDef().getGeometryType());
                            serverLog.addMessage(5, 200, "** getSpatialReference " + resultFeature.getFields().getField(f).getGeometryDef().getSpatialReference());
                            JSONConverterGeometry geoSerializer = new JSONConverterGeometry();
                            com.esri.arcgis.system.JSONObject fc_jsonobject = new com.esri.arcgis.system.JSONObject();
                            IGeometry fc_shape = (IGeometry) resultFeature.getValue(resultFeature.getFields().findField("SHAPE"));
                            geoSerializer.queryJSONGeometry(fc_shape, false, fc_jsonobject);
                            String fc_geometry = fc_jsonobject.toJSONString(null);
                            serverLog.addMessage(5, 200, "feature_geometry: " + fc_geometry);
                        }
                    }

                }
            }
        }
        return restRequestHandler.handleRESTRequest(capabilities, resourceName, operationName, operationInput,
                outputFormat, requestProperties, responseProperties);
    }

    /**
     * This method is called to handle SOAP requests.
     *
     * @param capabilities the capabilities
     * @param request      the request
     * @return the response as String
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    @Override
    public String handleStringRequest(String capabilities, String request) throws IOException, AutomationException {
        // Log message with server
        serverLog.addMessage(3, 200, "Request received in Sample Object Interceptor for handleStringRequest");

        /*
         * Add code to manipulate SOAP requests here
         */

        // Find the correct delegate to forward the request too
        IRequestHandler requestHandler = soiHelper.findRequestHandlerDelegate(so);
        if (requestHandler != null) {
            // Return the response
            return requestHandler.handleStringRequest(capabilities, request);
        }

        return null;
    }

    /**
     * This method is called by SOAP handler to handle OGC requests.
     *
     * @param httpMethod
     * @param requestURL          the request URL
     * @param queryString         the query string
     * @param capabilities        the capabilities
     * @param requestData         the request data
     * @param responseContentType the response content type
     * @param respDataType        the response data type
     * @return the response as byte[]
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    @Override
    public byte[] handleStringWebRequest(int httpMethod, String requestURL, String queryString, String capabilities,
                                         String requestData, String[] responseContentType, int[] respDataType) throws IOException, AutomationException {
        serverLog.addMessage(3, 200, "Request received in Sample Object Interceptor for handleStringWebRequest");

        /*
         * Add code to manipulate OGC (WMS, WFC, WCS etc) requests here
         */

        IWebRequestHandler webRequestHandler = soiHelper.findWebRequestHandlerDelegate(so);
        if (webRequestHandler != null) {
            return webRequestHandler.handleStringWebRequest(httpMethod, requestURL, queryString, capabilities, requestData,
                    responseContentType, respDataType);
        }

        return null;
    }

    /**
     * This method is called to handle schema requests for custom SOE's.
     *
     * @return the schema as String
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    @Override
    public String getSchema() throws IOException, AutomationException {
        serverLog.addMessage(3, 200, "Request received in Sample Object Interceptor for getSchema");

        /*
         * Add code to manipulate schema requests here
         */

        IRESTRequestHandler restRequestHandler = soiHelper.findRestRequestHandlerDelegate(so);
        if (restRequestHandler != null) {
            return restRequestHandler.getSchema();
        }

        return null;
    }

    /**
     * This method is called to handle binary requests from desktop.
     *
     * @param capabilities the capabilities
     * @param request
     * @return the response as byte[]
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    @Override
    public byte[] handleBinaryRequest2(String capabilities, byte[] request) throws IOException, AutomationException {
        serverLog.addMessage(3, 200, "Request received in Sample Object Interceptor for handleBinaryRequest2");

        /*
         * Add code to manipulate Binary requests from desktop here
         */

        IRequestHandler2 requestHandler = soiHelper.findRequestHandler2Delegate(so);
        if (requestHandler != null) {
            return requestHandler.handleBinaryRequest2(capabilities, request);
        }

        return null;
    }

    /**
     * This method is called to handle binary requests from desktop. It calls the
     * <code>handleBinaryRequest2</code> method with capabilities equal to null.
     *
     * @param request
     * @return the response as the byte[]
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    @Override
    public byte[] handleBinaryRequest(byte[] request) throws IOException, AutomationException {
        serverLog.addMessage(3, 200, "Request received in Sample Object Interceptor for handleBinaryRequest");

        /*
         * Add code to manipulate Binary requests from desktop here
         */

        IRequestHandler requestHandler = soiHelper.findRequestHandlerDelegate(so);
        if (requestHandler != null) {
            return requestHandler.handleBinaryRequest(request);
        }

        return null;
    }

    /**
     * shutdown() is called once when the Server Object's context is being shut down and is about to
     * go away.
     *
     * @throws IOException         Signals that an I/O exception has occurred.
     * @throws AutomationException the automation exception
     */
    public void shutdown() throws IOException, AutomationException {
        /*
         * The SOE should release its reference on the Server Object Helper.
         */
        this.serverLog.addMessage(3, 200, "Shutting down " + this.getClass().getName() + " SOI.");
        this.serverLog = null;
        this.so = null;
        this.soiHelper = null;
    }

    /**
     * Returns the ArcGIS home directory path.
     *
     * @return
     * @throws Exception
     */
    private String getArcGISHomeDir() throws IOException {
        String arcgisHome = null;
        /* Not found in env, check system property */
        if (System.getProperty(ARCGISHOME_ENV) != null) {
            arcgisHome = System.getProperty(ARCGISHOME_ENV);
        }
        if (arcgisHome == null) {
            /* To make env lookup case insensitive */
            Map<String, String> envs = System.getenv();
            for (String envName : envs.keySet()) {
                if (envName.equalsIgnoreCase(ARCGISHOME_ENV)) {
                    arcgisHome = envs.get(envName);
                }
            }
        }
        if (arcgisHome != null && !arcgisHome.endsWith(File.separator)) {
            arcgisHome += File.separator;
        }
        return arcgisHome;
    }

    private byte[] addTextWatermark(String watermarkText, BufferedImage sourceImage, String imageType,
                                    String outputFormat, File outputImageFile) throws IOException {
        // Create BufferedImage and Graphics2D objects
        Graphics2D g2d = (Graphics2D) sourceImage.getGraphics();

        // Initializes necessary graphic properties
        AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f);
        g2d.setComposite(alphaChannel);
        g2d.setColor(Color.BLUE);
        g2d.setFont(new Font("Arial", Font.BOLD, 64));
        FontMetrics fontMetrics = g2d.getFontMetrics();
        Rectangle2D rect = fontMetrics.getStringBounds(watermarkText, g2d);

        // Calculate the center
        int centerX = (sourceImage.getWidth() - (int) rect.getWidth()) / 2;
        int centerY = sourceImage.getHeight() / 2;

        // Add watermark at the center of image
        g2d.drawString(watermarkText, centerX, centerY);
        g2d.dispose();

        if (outputFormat.equalsIgnoreCase("image")) {
            return bufferedImagetoByteArray(sourceImage, imageType);
        } else if (outputFormat.equalsIgnoreCase("json")) {
            // replace watermarked image with original image
            writeImageToDisk(sourceImage, imageType, outputImageFile);
            return null;
        }

        return null;
    }

    private byte[] bufferedImagetoByteArray(BufferedImage sourceImage, String imageType) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(sourceImage, (imageType.startsWith("png")) ? "PNG" : "JPG", baos);
        baos.flush();
        byte[] imageInBA = baos.toByteArray();
        baos.close();
        return imageInBA;
    }

    private void writeImageToDisk(BufferedImage sourceImage, String imageType, File outputImageFile) throws IOException {
        ImageIO.write(sourceImage, (imageType.startsWith("png")) ? "PNG" : "JPG", outputImageFile);
    }

    private BufferedImage byteArrayToBufferedImage(byte[] sourceImageBytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(sourceImageBytes));
    }
}