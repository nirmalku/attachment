package com.vfi.ws.amdocs.cse;

import javax.activation.DataHandler;
import javax.xml.bind.annotation.XmlMimeType;

import com.vfi.ws.amdocs.repair.model.RepairBaseRequest;

public class Attachment{
	
	private String caseId;
	private String action;
	private String title;
	private String filename;
	@XmlMimeType("application/octet-stream")
	private DataHandler dataHandler;
	
	private String txt;
	
	private int t=1;
	
	public void SetTxt(int i){
		this.txt=String.valueOf(t);
	}
	public String getCaseId() {
		return caseId;
	}
	public void setCaseId(String caseId) {
		this.caseId = caseId;
	}
	public String getAction() {
		return action;
	}
	public void setAction(String action) {
		this.action = action;
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public DataHandler getDataHandler() {
		return dataHandler;
	}
	public void setDataHandler(@XmlMimeType("application/octet-stream") DataHandler dataHandler) {
		this.dataHandler = dataHandler;
	}
	public String getFilename() {
		return filename;
	}
	public void setFilename(String filename) {
		this.filename = filename;
	}
	
	
	

}
