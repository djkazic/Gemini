package net.api;

import java.io.File;
import java.util.ArrayList;

import org.json.JSONObject;
import org.restlet.ext.json.JsonRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import atrium.Core;
import atrium.NetHandler;
import atrium.Utilities;

/**
 * Specific REST hook for evaluation input
 *
 * @author Kevin Cai
 */
public class Bridge extends ServerResource {

	public void setHeaders() {
		getResponse().setAccessControlAllowOrigin("*");
	}

	@Post("application/text")
	public String process(JsonRepresentation entity) {
		JSONObject json = null;
		JSONObject responseJSON = new JSONObject();
		
		try {
			json = entity.getJsonObject();
			if(json.length() > 0) {
				Object oMethodCall = json.get("rpc");
				if(oMethodCall instanceof String) {
					String methodCall = (String) oMethodCall;

					switch(methodCall) {
						case "port_check":
							responseJSON.put("value", Core.config.cacheEnabled);
							break;

						case "peer_count":
							responseJSON.put("value", Core.peers.size());
							break;
							
						case "search":
							try {
								String query = json.getString("query");
								ArrayList<String[]> searchResults = NetHandler.doSearch(query);
								StringBuilder sb = new StringBuilder();
												sb.append("<h4>SEARCH RESULTS</h4>");
												sb.append("<div class=\"panel panel-default search-panel\">");
												sb.append("  <table class=\"table table-hover\" style=color:#333>");
												sb.append("  <thead><tr><th>#</th><th>TRACK</th><th>ARTIST</th><th>TIME</th></tr></thead>");
												
												sb.append("<tbody>");

												for(int i=0; i < searchResults.size(); i++) {
													sb.append("<tr>");
														sb.append("<td class=\"td-minus\">" + (i + 1) + "</td>");
														sb.append("<td class=\"td-plus\">");
															sb.append("<a href=\"#\">");
																sb.append("<i class=\"fa fa-play-circle-o\"></i>");
															sb.append("</a>");
														sb.append("</td>");
														sb.append("<td>");
															sb.append("Test Title");
														sb.append("</td>");
														sb.append("<td>");
															sb.append("Test Artist");
														sb.append("</td>");
														sb.append("<td class=\"td-dubplus\">");
															sb.append("1:23");
														sb.append("</td>");
														sb.append("<td class=\"td-plus\">");
															sb.append("<a href=\"#\">");
																sb.append("<i class=\"fa fa-check-circle-o\"></i>");
															sb.append("</a>");
														sb.append("</td>");
													sb.append("</tr>");
												}
								
												sb.append("</tbody>");
												sb.append("</table>");
												sb.append("</div>");
								responseJSON.put("value", sb.toString());
							} catch (Exception ex) {
								Utilities.log(this, "rpc_search error: " + ex.getMessage(), false);
								responseJSON.put("error", ex.getMessage());
							}
							break;

						default:
							Utilities.log(this, "Unknown RPC called: " + methodCall, false);
							responseJSON.put("error", "unknown_rpc");
							break;
					}
				}
			} else {
				responseJSON.put("error", "empty_params");
			}
		} catch (Exception e) {
			try {
				responseJSON.put("error", "no_rpc_defined");
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		setHeaders();
		return responseJSON.toString();
	}

	@Get
	public String toString() {
		return "INVALID: API GET ACCESS DISALLOWED";
	}
}