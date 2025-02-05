package com.diners.wstransaccionas400;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.diners.logs.GestionLogs;
import com.diners.logs.utils.ConstantesLogs;
import com.diners.logs.utils.Propiedades;
import com.diners.wstransaccionas400.comunicacion.ISeriesCall;
import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbUserException;
import com.ibm.broker.plugin.MbXMLNSC;
import com.diners.wstransaccionas400.comunicacion.IseriesCallConexion;

public class WSTransaccionAS400 extends MbJavaComputeNode  {
	
	static GestionLogs logs;
	public void evaluate(MbMessageAssembly inAssembly) throws MbException {
		MbOutputTerminal out = getOutputTerminal("out");
		MbOutputTerminal outError = getOutputTerminal("failure");
		MbMessage inMessage = inAssembly.getMessage();
		MbMessage outMessage = new MbMessage();
		
		copyMessageHeaders(inMessage, outMessage);
		
		try {
			/* Obtener �rboles principales para el procesamiento */
			MbElement mensajeIn = inAssembly.getMessage().getRootElement();
			MbElement mensajeEnv = inAssembly.getGlobalEnvironment().getRootElement();
			MbElement messageHeaderIn = mensajeEnv.getFirstElementByPath("listaAs400/conexion");
			MbElement ruteo = mensajeEnv.getFirstElementByPath("Variable/ruteoFlujo");
			MbElement nomArchi = mensajeEnv.getFirstElementByPath("Variable/nomArchivoLogs");
			

			// create new message as a copy of the input
			MbMessage inLocal = inAssembly.getLocalEnvironment();		    
			String nombreArchivoLogs = nomArchi.getValueAsString();
			Propiedades prop = new Propiedades();
			prop.setRutaArchivo(prop.obtenerPropiedad("archivoLOGS.rutaLogs.genericos",nombreArchivoLogs));
			prop.setNivelTrace("INFO");
			prop.setPatron("%d{dd/MM/yyyy HH:mm:ss,SSS} - %m%n");
			
			logs = new GestionLogs(prop);
			
			// ----------------------------------------------------------
			// Add user code below
			
			String idReply = mensajeEnv.getFirstElementByPath("log").getFirstElementByPath("id").getValueAsString();
			
			String moduloRPG = messageHeaderIn.getFirstElementByPath("moduloRPG").getValueAsString();
			String programName = messageHeaderIn.getFirstElementByPath("programa").getValueAsString();
			String hostname = messageHeaderIn.getFirstElementByPath("hostname").getValueAsString();
			String commandRPG = messageHeaderIn.getFirstElementByPath("comandoRPG").getValueAsString();
			String library = messageHeaderIn.getFirstElementByPath("libraryList").getValueAsString();
			String limCache = messageHeaderIn.getFirstElementByPath("limpiarCache").getValueAsString();
			String rutaLogs = messageHeaderIn.getFirstElementByPath("rutaLogs").getValueAsString();
			String threadId = Thread.currentThread().getId() + "";
			logs.imprimirLogsJava("[Thread: "+ threadId + "] "+idReply + "-----------------------------" + moduloRPG + "_" + programName +"------------------------------- ");
			MbElement mensajeListaAs400 = mensajeEnv.getFirstElementByPath("listaAs400");
			try {
				
				byte[] msgByteStreamOut =null;
				if (!rutaLogs.equals("")) {
					ISeriesCall.enableTrace(buildFileName(rutaLogs, programName));
				}
								
				IseriesCallConexion iseries = new IseriesCallConexion(hostname, programName,library,commandRPG, logs, threadId);
				if (limCache.equalsIgnoreCase("0")) {
					
					MbElement blob = mensajeIn.getLastChild().getLastChild();
					byte[] msgValue=(byte[]) blob.getValue();
					msgByteStreamOut = iseries.callXSLT(hostname, moduloRPG, programName, library, msgValue, idReply,logs, threadId, true);																        
					logs.imprimirLogsJava("[Thread: "+ threadId + "] "+idReply + "Recuperando mensaje de salida...");					
					outMessage.getRootElement().createElementAsLastChildFromBitstream(msgByteStreamOut, "XMLNSC", "", "", "", 546, 819, 1208);
					
					MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);
					
					MbElement resultado = mensajeEnv.createElementAsLastChild(MbElement.TYPE_NAME);
					resultado.setName("salida");
					resultado.createElementAsLastChild(MbXMLNSC.FIELD,"codError", "0");
					resultado.createElementAsLastChild(MbXMLNSC.FIELD,"descError", "OK");
					
					if (ruteo.getValueAsString().equalsIgnoreCase("HTTPJson")) {
						inLocal.getRootElement().getFirstElementByPath("XSL").delete();
					}
					
					logs.imprimirLogs("[Thread: "+ threadId + "] "+idReply + "-----------------------------FIN_" + moduloRPG + "_" + programName +"------------------------------- ");
					out.propagate(outAssembly);
					
				}else if (limCache.equalsIgnoreCase("1")){
					logs.imprimirLogs("[Thread: "+ threadId + "] "+idReply + "Numero de conexiones instanciadas :: " + iseries.removeContext(hostname, programName, library,threadId));
					
                	MbElement resultado = mensajeEnv.createElementAsLastChild(MbElement.TYPE_NAME);
    				resultado.setName("salida");
    				resultado.copyElementTree(mensajeListaAs400);
    				resultado.createElementAsLastChild(MbXMLNSC.FIELD,"codError", "010");
    				resultado.createElementAsLastChild(MbXMLNSC.FIELD,"descError", "Cache limpiada");
    				
    				MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);
    				out.propagate(outAssembly);
				
				}
				
			} catch (Exception ex) {
				ex.printStackTrace();
				String error = "Error no controlado: -- " + ex.getMessage();
				MbElement resultado = mensajeEnv.createElementAsLastChild(MbElement.TYPE_NAME);
				resultado.setName("salida");
				resultado.copyElementTree(mensajeListaAs400);
				resultado.createElementAsLastChild(MbXMLNSC.FIELD,"codError", "999");
				resultado.createElementAsLastChild(MbXMLNSC.FIELD,"descError", error);
				
				logs.imprimirLogs("[Thread: "+ threadId + "] "+idReply + "--Error encontrado: " + error);
				
				MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);
				outError.propagate(outAssembly);
			}
			
			// End of user code
			// ----------------------------------------------------------
		} catch (MbException e) {
			throw e;
		} catch (RuntimeException e) {
 			throw e;
		} catch (Exception e) {
			throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
		} finally {
			outMessage.clearMessage();
		}
	}
	
	protected void copyMessageHeaders(MbMessage inMessage, MbMessage outMessage) throws MbException
	{
		MbElement outRoot = outMessage.getRootElement();
		MbElement header = inMessage.getRootElement().getFirstChild();
		
		while ((header != null) && (header.getNextSibling() != null))
	    {
	    	outRoot.addAsLastChild(header.copy());
	    	header = header.getNextSibling();
	    }
		
	}
	
	protected String buildFileName(String directory, String nomPCML)
	{
		String fileName = null;
	    DateFormat hourdateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
		Date fecha = new Date();
		String fechaActual = hourdateFormat.format(fecha);

	    String OS = System.getProperty("os.name").toLowerCase();
	    
	    if (OS.indexOf("windows") > -1){
	    	fileName = directory + "\\" + "Trace_PCML_" + nomPCML + "_" + fechaActual.toString();
	    }
	    else {
	    	fileName = directory + "/" + "Trace_PCML_" + nomPCML + "_" + fechaActual.toString();
	    }

	    return fileName;
	}
	
	/**
	 * Imprimir log en archivos
	 * @param cadena
	 * @param nomServicio
	 * @param nivelTrace
	 */
	public static void imprimirLog(String cadena, String nomServicio, String nivelTrace){
		if (cadena != "" || cadena != null) {
			Propiedades prop = new Propiedades();
			
			prop. setRutaArchivo(prop.obtenerPropiedad(ConstantesLogs.ETIQUETA_RUTA_LOGS_GENERICOS,nomServicio));
			
			prop.setNivelTrace(nivelTrace);
			prop.setPatron(ConstantesLogs.PATRON_LOGS_GENERICOS);
			
			GestionLogs logs = new GestionLogs(prop);			
			logs.imprimirLogs(cadena);
		}
	}
	 
}
