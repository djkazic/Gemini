package io;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import atrium.Core;

public class Metadata {
	
	private String bfChecksum;
	private int ups;
	private int downs;
	private Map<String, Object[]> comments;
	private boolean voted;
	private long timestamp;

	public Metadata() {
		bfChecksum = null;
		ups = 0;
		downs = 0;
		comments = null;
		voted = false;
		timestamp = 0;
	}
	
	public void addComment(String comment) {
		String signKey = Core.rsa.sign(comment);
		if(comments == null) {
			comments = new HashMap<String, Object[]> ();
		}
		comments.put(comment, new Object[] {Core.rsa.rawPublicKey(), signKey});
		timestamp = System.currentTimeMillis();
	}
	
	public int getScore() {
		return ups - downs;
	}
	
	public ArrayList<String> getComments() {
		if(timestamp <= System.currentTimeMillis()) {
			ArrayList<String> approved = new ArrayList<String> ();
			for(Entry<String, Object[]> entry : comments.entrySet()) {
				String comment = (String) entry.getKey();
				Object[] pkAndSignature = (Object[]) entry.getValue();
				PublicKey pubKey = (PublicKey) pkAndSignature[0];
				String signature = (String) pkAndSignature[1];
				if(Core.rsa.verify(comment, pubKey, signature)) {
					approved.add(comment);
				}
			}
			return approved;
		}
		return null;
	}
	
	public void vote(int upDown) {
		if(!voted) {
			voted = true;
			if(upDown == 1) {
				ups++;
			} else if(upDown == -1) {
				downs++;
			}
		}
	}
}
