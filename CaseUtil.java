package com.vfi.ws.amdocs.cse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.verifone.crm.server.integration.datatype.common.UserEDT;
import com.verifone.crm.server.integration.datatype.kcase.AttributesEDT;
import com.verifone.crm.server.integration.datatype.kcase.CaseCreateInputEDT;
import com.verifone.crm.server.integration.datatype.kcase.CaseCreateOutputEDT;
import com.verifone.crm.server.integration.datatype.kcase.CaseDetailsInputEDT;
import com.verifone.crm.server.integration.datatype.kcase.CaseDetailsOutputEDT;
import com.verifone.crm.server.integration.datatype.kcase.CasePartDetailsEDT;
import com.verifone.crm.server.integration.datatype.kcase.HeaderDetailsEDT;
import com.verifone.crm.server.integration.datatype.site.AddressEDT;
import com.verifone.crm.server.integration.ejb.kcase.CaseServicesRemote;
import com.vfi.ws.amdocs.common.Attribute;
import com.vfi.ws.amdocs.common.Response;
import com.vfi.ws.amdocs.flex.bean.FlexUtil;
import com.vfi.ws.amdocs.repair.model.RepairBaseResponse;
import com.vfi.ws.amdocs.service.AmdocsService;
import com.vfi.ws.amdocs.site.Site;
import com.vfi.ws.amdocs.site.SiteUtil;
import com.vfi.ws.amdocs.util.Configuration;
import com.vfi.ws.amdocs.util.ServiceUtil;

public class CaseUtil {
	
	private Logger logger = Logger.getLogger(this.getClass());
	
	private final static String FILE_TYPES[] = new String[]{
			"DOC", "DOCX", "LOG", "MSG", "ODT", "PAGES", "RTF", "TEX", "TXT", "WPS"
			, "CSV", "DAT", "GED", "KEY", "PPS", "PPT", "PPTX"
			, "SDF", "TAR", "PDF", "VCF", "XML", "AIF", "IFF"
			, "M3U", "M4A", "MID", "MP3", "MPA", "WAV", "WMA"
			, "3GP", "AVI", "FLV", "MOV", "MP4", "VOB", "WMV"
			, "BMP", "GIF", "JPG", "PNG", "PSD", "TGA", "TIF"
			, "TIFF", "YUV", "PCT", "INDD", "XLR", "XLS", "XLSX"
			, "HTM", "HTML", "XHTML", "ZIP", "TAR", "TAR.GZ", "RAR", "7Z", "PKG", "ZIPX", "TMP"};
	
	
	
	public RepairBaseResponse createCaseAttachment(Attachment request) {
		logger.info("inside attachment");
		
		RepairBaseResponse response = new RepairBaseResponse();
		CallableStatement statement = null;
		FileOutputStream fos = null;
		Connection connection = null;
		try{
			connection = ServiceUtil.getInstance().getDBConnectionForCRM();
			
			String amdocsDirectory = "/AMDOCS/case";
			long caseObjid = getCaseObjid(connection, request.getCaseId());
			logger.info("Case Objid for case number ["+request.getCaseId()+"] is " + caseObjid);
			
			if(caseObjid == 0){
				throw new Exception("Case ID is not valid.");
			}
			
			if(!(this.isAcceptedFileType(request.getFilename()))){
				throw new Exception("File type not accepted.");
			}
			
			String caseDirectory = amdocsDirectory + "/" + caseObjid;
			
			
			
			
			File caseDirectoryFile = new File(caseDirectory);
			File caseAttachmentFile = new File(caseDirectory + "/" + request.getFilename());
			
			if(!caseDirectoryFile.exists()){
				logger.info("Creating directory " + caseDirectory);
				boolean bool = caseDirectoryFile.mkdirs();
				logger.info("Creating directory result: " + bool);
				caseDirectoryFile.setReadable(true, false);
				caseDirectoryFile.setWritable(true, false);
			}
			
			logger.info("Creating directory exists: " + caseDirectoryFile.exists());
			
			if(!caseDirectoryFile.exists()){
				throw new Exception("Directory " + caseDirectory + " does not exists.");
			}

			if(caseAttachmentFile.exists()){
				throw new Exception("An attachment already exists with the same name in the given case ["+request.getCaseId()+"].");
			}
			
			logger.info("Writing file " + caseAttachmentFile.getAbsolutePath());
			
			InputStream is = request.getDataHandler().getInputStream();
			
			byte buffer[] = new byte[128];
			int len = 0;
			
			fos = new FileOutputStream(caseAttachmentFile);
			
			while( (len=is.read(buffer)) > -1 ){
				fos.write(buffer, 0, len);
				fos.flush();
			}
			caseAttachmentFile.setReadable(true, false);
			caseAttachmentFile.setWritable(true, false);
			
			if(caseAttachmentFile.exists()){
				double bytes = caseAttachmentFile.length();
				double kilobytes = (bytes / 1024);
				double megabytes = (kilobytes / 1024);
				
				logger.info("File size: " + megabytes);
				
				if(megabytes>5){
					caseAttachmentFile.delete();
					throw new Exception("Allowed file size is only <5MB.");
				}
			}
			
			
			statement = connection.prepareCall("{ call CLFY_API_CASE_PKG.X_ADD_ATTACHMENT(?,?,?,?,?,?) }");
			statement.registerOutParameter(5, Types.INTEGER);
			statement.registerOutParameter(6, Types.VARCHAR);

			if (request.getTitle() == null || request.getTitle().equals("")) {
				request.setTitle(request.getFilename());
			}

			statement.setString(1, request.getCaseId());
			statement.setString(2, request.getTitle());
			statement.setString(3, caseAttachmentFile.getAbsolutePath());
			statement.setString(4, "bvinterface");

			statement.execute();

			int p_status = statement.getInt(5);
			String p_status_msg = statement.getString(6);
			logger.info("p_status: " + p_status);
			logger.info("p_status_msg: " + p_status_msg);
			
			response.setMessage(p_status_msg);
			response.setErrorCode(String.valueOf(p_status));
			response.setStatus("SUCCESS");
			
			if(p_status!=0){
				throw new Exception(p_status_msg);
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			response.setMessage(e.getMessage());
			response.setStatus(AmdocsService.FAILURE);
			return response;
		} finally {
			try {
				statement.close();
			} catch (Exception e) {
			}
			try{
				connection.close();
			}catch(Exception e){}
			try{
				fos.close();
			}catch(Exception e){}
		}
		
		return response;
	}
	
	public CaseResponse createCase(Site site, Case cse){
		CaseResponse response = new CaseResponse();
		
		Connection connection = null;
		try{
			connection = ServiceUtil.getInstance().getDBConnectionForCRM();
			
			CaseCreateInputEDT input = new CaseCreateInputEDT();
			CaseDetailsInputEDT[] caseInput = new CaseDetailsInputEDT[1];
			
			if(site!=null && (site.getShipToSiteId()==null || site.getShipToSiteId().equals(""))){
				logger.info("Site id is empty. Trying to create a site.");
				
				Response siteResponse = new SiteUtil().createSite(site, true);
				
				logger.info("Site Creation Response Code: " + siteResponse.getCode());
				logger.info("Site Creation Response Msg: " + siteResponse.getMessage());
								
				if(!siteResponse.getCode().equalsIgnoreCase("SUCCESS")){
					response.setCode(siteResponse.getCode());
					response.setMessage(siteResponse.getMessage());
					return response;
				}else{
					response.setMessage(siteResponse.getMessage());
				}
			}
			
			
			
			caseInput[0] = new CaseDetailsInputEDT();
			
			caseInput[0].setOriginatorSystem(cse.getUsername());
			
			caseInput[0].setBankWO(cse.getBankWorkOrder());
			logger.info("Case Bank WO: " + caseInput[0].getBankWO());
			
			caseInput[0].setCaseType1(cse.getCaseType1());
			logger.info("Case Type 1: " + caseInput[0].getCaseType1());
			
			caseInput[0].setCaseType2(cse.getCaseType2());
			logger.info("Case Type 2: " + caseInput[0].getCaseType2());
			
			caseInput[0].setCaseType3(cse.getCaseType3());
			logger.info("Case Type 3: " + caseInput[0].getCaseType3());
			
			caseInput[0].setCommitmentActionType(cse.getCommitmentAction());
			logger.info("Commitment type: " + caseInput[0].getCommitmentActionType());
			
			if(cse.getCommitmentDate()!=null) caseInput[0].setCommitmentDate(cse.getCommitmentDate().getTime());
			logger.info("Commitment Date: " + caseInput[0].getCommitmentDate());
			
			caseInput[0].setSiteId(site.getShipToSiteId());
			logger.info("Site ID: " + caseInput[0].getSiteId());
			
			caseInput[0].setContactFirstName(site.getContactFirstName());
			logger.info("Case First Name: " + caseInput[0].getContactFirstName());
			
			caseInput[0].setContactLastName(site.getContactLastName());
			logger.info("Case Last Name: " + caseInput[0].getContactLastName());
			
			caseInput[0].setWorkgroup(cse.getWorkgroup());
			logger.info("Case Workgroup: " + caseInput[0].getWorkgroup());
			
			caseInput[0].setCommitmentTitle(cse.getCommitmentTitle());
			logger.info("Commitment Title: " + caseInput[0].getCommitmentTitle());
			
			caseInput[0].setContractId(cse.getContractId());
			logger.info("Contract ID: " + caseInput[0].getContractId());
			
			caseInput[0].setInitialStatus(cse.getStatus());
			logger.info("Initial Status: " + caseInput[0].getInitialStatus());
			
			caseInput[0].setNotes(cse.getNotes());
			logger.info("Notes: " + caseInput[0].getNotes());
			
			caseInput[0].setNotesType(cse.getNoteType());
			logger.info("Notes Type: " + caseInput[0].getNotesType());
			
			caseInput[0].setOwner(cse.getOwner());
			logger.info("Case Owner: " + caseInput[0].getOwner());
			
			
			caseInput[0].setCommunicationType(cse.getCommunicationType());
			logger.info("Case Owner: " + caseInput[0].getCommunicationType());
			
			caseInput[0].setTid(cse.getNewAppTid());
			logger.info("TID: " + caseInput[0].getTid());
			
			
			caseInput[0].setInstalledAppTID(cse.getInstalledAppTid());
			logger.info("Terminal TID: " + caseInput[0].getInstalledAppTID());
			

			
			caseInput[0].setMerchantID(site.getMerchantRefId());
			logger.info("Merchant ID : " + caseInput[0].getMerchantID());
			
			caseInput[0].setBillToSiteID(site.getBillToSiteId());
			logger.info("BillToSiteId: " + caseInput[0].getBillToSiteID());
			
			caseInput[0].setPlActionType(cse.getPhoneLogActionType());
			logger.info("PL Action Type: " + caseInput[0].getPlActionType());
			
			if(cse.getPhoneLogSite()!=null){
				caseInput[0].setPlFirstName(cse.getPhoneLogSite().getContactFirstName());
				logger.info("PL FName: " + caseInput[0].getPlFirstName());
				
				caseInput[0].setPlLastName(cse.getPhoneLogSite().getContactLastName());
				logger.info("PL LName: " + caseInput[0].getPlLastName());
				
				caseInput[0].setPlSiteId(cse.getPhoneLogSite().getShipToSiteId());
				logger.info("PL Site ID: " + caseInput[0].getPlSiteId());
			}
			caseInput[0].setPriority(cse.getPriority());
			logger.info("Case Priority: " + caseInput[0].getPriority());
			
			caseInput[0].setQueue(cse.getQueue());
			logger.info("Case Queue: " + caseInput[0].getQueue());
			
			caseInput[0].setSeverity(cse.getSeverity());
			logger.info("Case Severity: " + caseInput[0].getSeverity());
			
			caseInput[0].setSuppProgId(cse.getSupportProgramId());
			logger.info("Case Supp Prog ID: " + caseInput[0].getSuppProgId());
			
			caseInput[0].setTemplateId(cse.getCaseTemplateId());
			logger.info("Case Template ID: " + caseInput[0].getTemplateId());
			
			caseInput[0].setTitle(cse.getTitle());
			logger.info("Case Title: " + caseInput[0].getTitle());
			
			caseInput[0].setId("" + getNextID(connection));
			caseInput[0].setOperatingUnit(cse.getOperatingUnit());
			logger.info("Case OU: " + caseInput[0].getOperatingUnit());
			
			caseInput[0].setCustomerRef(cse.getCustomerTicketRef());
			logger.info("Case Cust Ref: " + caseInput[0].getCustomerRef());
			
			caseInput[0].setWarningHours("001 30:00");
			
			caseInput[0].setPhone(site.getPhoneNumber());
			logger.info("Case Phone: " + caseInput[0].getPhone());
			
			caseInput[0].setSerialNumber(cse.getCaseSerialNumber());
			logger.info("Case SerialNumber: " + caseInput[0].getSerialNumber());
			
			caseInput[0].setPartNumber(cse.getCasePartNumber());
			logger.info("Case PartNumber: " + caseInput[0].getPartNumber());
			
			if(cse.getAlternateAddres()!=null){
			
				AddressEDT alternateAddress = new AddressEDT();
				alternateAddress.setAddress1(cse.getAlternateAddres().getAddress1());
				alternateAddress.setAddress2(cse.getAlternateAddres().getAddress2());
				alternateAddress.setAddress3(cse.getAlternateAddres().getAddress3());
				alternateAddress.setCity(cse.getAlternateAddres().getCity());
				alternateAddress.setCountry(cse.getAlternateAddres().getCountry());
				alternateAddress.setState(cse.getAlternateAddres().getState());
				alternateAddress.setZipCode(cse.getAlternateAddres().getZipCode());
				
				caseInput[0].setAltContactAddressEDT(alternateAddress);
			
			}
					
			if(cse.getAttributes()!=null && cse.getAttributes().length>0){
				
				/** need to put other list and exclude attributes with empty values **/
				/** name not null/empty and value not null/empty **/
				List<Attribute> attrs = new ArrayList<Attribute>();
				for(Attribute attr:cse.getAttributes()){
					if(attr.getName()!=null && !attr.getName().trim().equals("") && attr.getValue()!=null && !attr.getValue().trim().equals("")){
						attrs.add(attr);
					}
				}
				
				AttributesEDT[] attributesList = new AttributesEDT[attrs.size()];
				int counter=0;
				
				for(Attribute attr:attrs){
					attributesList[counter]=new AttributesEDT();
					attributesList[counter].setAttributeName(attr.getName());
					attributesList[counter++].setAttributeValue(attr.getValue());
					
					logger.info("\tAttr Name: " + attr.getName());
					logger.info("\tAttr Value: " + attr.getValue());
				}
				
				if(attributesList!=null && attributesList.length>0){
					caseInput[0].setAttributesList(attributesList);
				}
				
				//caseInput[0].setAttributesList(attributesList);
			}
			
			
			if(cse.getCasePartDetails()!=null && cse.getCasePartDetails().getCasePartDetail()!=null && cse.getCasePartDetails().getCasePartDetail().length>0){
				int length = cse.getCasePartDetails().getCasePartDetail().length;
				CasePartDetailsEDT[] details = new CasePartDetailsEDT[length];
				
				for(int i=0; i<length; i++){
					CasePartDetail inputDetail = cse.getCasePartDetails().getCasePartDetail()[i];
					details[i] = new CasePartDetailsEDT();
					details[i].setQuantity(String.valueOf(inputDetail.getQuantity()));
					details[i].setPartNumber(inputDetail.getPartNumber());
					details[i].setSerialNumber(inputDetail.getSerialNumber());
					details[i].setTerminalID(inputDetail.getTid());
					details[i].setDOA(inputDetail.isDoa());
					
					details[i].setRequestType(inputDetail.getRequestType());
					details[i].setInvLocation(inputDetail.getInvLocation());
					details[i].setInvBinName(inputDetail.getInvBinName());
					details[i].setPrtDtlsObjid(inputDetail.getPrtDtlsObjid());
					
					logger.info("P/N: " + details[i].getPartNumber());
					logger.info("S/N: " + details[i].getSerialNumber());
				}
				caseInput[0].setCasePartDetailsList(details);
			}else{
				CasePartDetailsEDT[] detail = new CasePartDetailsEDT[1];
				
				detail[0] = new CasePartDetailsEDT();
				detail[0].setQuantity("1");
				detail[0].setPartNumber(cse.getPartNumber());
				detail[0].setSerialNumber(cse.getSerialNumber());
				
				logger.info("P/N: " + detail[0].getPartNumber());
				logger.info("S/N: " + detail[0].getSerialNumber());
				caseInput[0].setCasePartDetailsList(detail);
			}
			
			long currentTime = System.currentTimeMillis();
			
			HeaderDetailsEDT header = new HeaderDetailsEDT();
			header.setBpelFileName("B2B-AmdocsService-" + currentTime);
			header.setCallerInstanceId("10000");
			header.setInterfaceId("10000");
			header.setUserGroup("US");
			input.setHeaderDetailsEDT(header);
			input.setCaseDetailstInputEDT(caseInput);
			
			
			CaseServicesRemote remote = ServiceUtil.getInstance().getCaseServices();
			
			UserEDT userEDT = new UserEDT();
			userEDT.setPassword(Configuration.getInstance().getProperty("b2b.amdocs.password"));
			userEDT.setUserName(Configuration.getInstance().getProperty("b2b.amdocs.username"));
			
			logger.info("Creating case...: " + remote);
			CaseCreateOutputEDT amdocsResponse = remote.createCase(userEDT, input);
			
			//logger.info("Got amdocsResponse: " + amdocsResponse);
			//logger.info("Got amdocsResponse.getCaseDetailsOutputEDT: " + amdocsResponse.getCaseDetailsOutputEDT());
			logger.info("Got amdocsResponse.getCaseDetailsOutputEDT.length: " + amdocsResponse.getCaseDetailsOutputEDT().length);
			
			CaseDetailsOutputEDT output = amdocsResponse.getCaseDetailsOutputEDT()[0];
			logger.info("CaseDetailsOutputEDT@index[0]: " + output);
			
			if(output==null){
				response.setCode(AmdocsService.FAILURE);
				response.setMessage("CaseDetailsOutputEDT@index[0] is null. Could not get correct details.");
				return response;
			}
			
			String status = output.getStatus();
			logger.info("Creating case status: " + status);
			String message = output.getMessage();
			logger.info("Creating case message: " + message);
			String caseId = output.getCaseId();
			logger.info("Creating case id: " + caseId);
			String custTicketRef = output.getCustTicketRef();
			logger.info("custTicketRef: " + custTicketRef);
			
			
			
			if(status == null || !status.equalsIgnoreCase("SUCCESS")){
				response.setCode(status);
				response.setMessage(message);
			}else{
				response.setCode(status);
				response.setSiteId(site.getShipToSiteId());
				response.setCaseId(caseId);
				
				String mid = site.getMerchantRefId();
				if(mid!=null){
					if(mid.indexOf("~")>-1){
						mid = mid.substring(0, mid.indexOf("~"));
						logger.info("mid: " + mid);
					}
					response.setMerchantId(mid);
				}
				response.setCustomerSORef(custTicketRef);
			}
			
		}catch(NullPointerException e){
			logger.error(e);
			response.setCode(AmdocsService.FAILURE);
			response.setMessage("NullPointerException: " + e.getMessage());
			
			new FlexUtil().sendErrorEmail(cse, e);
		}catch(InvalidClassException e){
			logger.error(e);
			response.setCode(AmdocsService.FAILURE);
			response.setMessage("Incompatible Amdocs EJB client JAR file (VfiIntegration.jar). " + e.getMessage());
			
			new FlexUtil().sendErrorEmail(cse, e);
		}catch(Exception e){
			logger.error(e);
			response.setCode(AmdocsService.FAILURE);
			response.setMessage("Unknown error: " + e.getMessage());
			
			new FlexUtil().sendErrorEmail(cse, e);
		}finally{
			try{
				if(connection!=null) connection.close();
			}catch(Exception e){}
		}
		
		if(response.getCode()!=null) response.setCode(response.getCode().toUpperCase());
		
		return response;
		
	}
	
	public void validateCaseForCreate(Case cse, Site site) throws Exception{
		
		if(cse.getTitle() == null || cse.getTitle() == null){
			throw new Exception("Case title is required.");
		}else if(cse.getOwner() == null || cse.getOwner().equals("")){
			throw new Exception("Case owner is required.");
		}else if(cse.getWorkgroup() == null || cse.getWorkgroup().equals("")){
			throw new Exception("Workgroup is required.");
		}else if(cse.getCaseType1() == null || cse.getCaseType1().equals("")){
			throw new Exception("Case type 1 is required.");
		}else if(cse.getCaseType2() == null || cse.getCaseType2().equals("")){
			throw new Exception("Case type 2 is required.");
		}else if(site.getShipToSiteId() == null && site.getShipToSiteRef() == null && site.getMerchantRefId() == null){
			throw new Exception("Ship-to site id or ship-to site ref is required.");
		}else if(site.getShipToSiteRef() != null && site.getShipToSiteRefType() == null){
			throw new Exception("Ship-to site ref type is required.");
		}
		
	}
	
	private long getCaseObjid(Connection conn, String caseId){
		String sql = "select objid from table_case where id_number=?";
		long id = 0;
		PreparedStatement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.prepareStatement(sql);
			stmt.setString(1, caseId);
			rs = stmt.executeQuery();
			if (rs.next()) {
				id = rs.getLong("OBJID");
			}
		} catch (Exception e) {
			logger.error("getCaseObjid: " + e.getMessage(), e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			} catch (Exception e) {
				stmt = null;
				rs = null;
			}
		}

		return id;
	}
	
	private long getNextID(Connection conn) {
		long id = 0;
		String sql = "";
		Statement stmt = null;
		ResultSet rs = null;
		try {
			stmt = conn.createStatement();
			sql = "SELECT VFI_INTERFACE.X_MASS_CASE_SEQ.NEXTVAL NEXT_ID FROM DUAL";
			rs = stmt.executeQuery(sql);
			if (rs.next()) {
				id = rs.getLong("NEXT_ID");
			}
		} catch (Exception e) {
			logger.error("getNextID: " + e.getMessage(), e);
		} finally {
			try {
				if (rs != null)
					rs.close();
				if (stmt != null)
					stmt.close();
			} catch (Exception e) {
				stmt = null;
				rs = null;
			}
		}

		return id;
	}
	
	
	private boolean isAcceptedFileType(String filename){
		
		if(filename==null) return false;
	
		for(String type:FILE_TYPES){
			if(filename.toUpperCase().trim().endsWith(type)) return true;
		}
		
		return false;
	}
	
}
