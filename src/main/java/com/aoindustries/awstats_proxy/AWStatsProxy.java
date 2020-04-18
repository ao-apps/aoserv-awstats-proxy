/*
 * aoserv-awstats-proxy - Webapp that publishes AWStats reports from the AOServ Platform.
 * Copyright (C) 2006-2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of aoserv-awstats-proxy.
 *
 * aoserv-awstats-proxy is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * aoserv-awstats-proxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with aoserv-awstats-proxy.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.awstats_proxy;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.SSLConnector;
import com.aoindustries.aoserv.client.TCPConnector;
import com.aoindustries.aoserv.client.linux.User;
import com.aoindustries.aoserv.client.web.Site;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.AOPool;
import com.aoindustries.net.DomainName;
import com.aoindustries.net.HostAddress;
import com.aoindustries.net.Port;
import com.aoindustries.net.Protocol;
import com.aoindustries.validation.ValidationException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Provides access to AWStats files.
 * 
 * @author  AO Industries, Inc.
 */
@WebServlet("/*")
public class AWStatsProxy extends HttpServlet {

	private static final long serialVersionUID = 1L;

    /**
     * Gets the AOServConnector for this application.
     */
    private AOServConnector getAOServConnector() throws IOException {
		try {
			// Get the parameters
			ServletContext context=getServletContext();
			String protocol=context.getInitParameter("com.aoindustries.awstats_proxy.Connector.protocol");
			HostAddress hostname = HostAddress.valueOf(context.getInitParameter("com.aoindustries.awstats_proxy.Connector.hostname"));
			Port port = Port.valueOf(
				Integer.parseInt(context.getInitParameter("com.aoindustries.awstats_proxy.Connector.port")),
				Protocol.TCP
			);
			int poolSize=Integer.parseInt(context.getInitParameter("com.aoindustries.awstats_proxy.Connector.pool_size"));
			User.Name username = User.Name.valueOf(context.getInitParameter("com.aoindustries.awstats_proxy.Connector.username"));
			String password=context.getInitParameter("com.aoindustries.awstats_proxy.Connector.password");

			// Get the connector
			if(protocol.equalsIgnoreCase("ssl")) {
				return SSLConnector.getSSLConnector(
					hostname,
					null,
					port,
					username,
					username,
					password,
					null,
					poolSize,
					AOPool.DEFAULT_MAX_CONNECTION_AGE,
					null,
					null
				);
			} else if(protocol.equalsIgnoreCase("tcp")) {
				return TCPConnector.getTCPConnector(
					hostname,
					null,
					port,
					username,
					username,
					password,
					null,
					poolSize,
					AOPool.DEFAULT_MAX_CONNECTION_AGE
				);
			} else throw new IllegalArgumentException("Unexpected protocol: "+protocol);
		} catch(ValidationException e) {
			throw new IOException(e);
		}
    }

    @Override
    public void doGet(
        HttpServletRequest request,
        HttpServletResponse response
    ) throws IOException, ServletException {
        try {
            // Get the connection to the master server
            AOServConnector conn=getAOServConnector();

            // Resolve the list of possible websites
            ServletContext context=getServletContext();
            String site_name=context.getInitParameter("com.aoindustries.awstats_proxy.site_name");
            String server=context.getInitParameter("com.aoindustries.awstats_proxy.server");
            List<Site> sites = new ArrayList<>();
            for(Site site : conn.getWeb().getSite().getRows()) {
                if(
                    (site_name==null || site_name.length()==0 || site_name.equals(site.getName()))
                    && (server==null || server.length()==0 || server.equals(site.getLinuxServer().getHostname().toString()))
                ) {
                    sites.add(site);
                    System.err.println("DEBUG: AWStatsProxy: Found matching site: "+site);
                }
            }

            // If none possible, error
            if(sites.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No websites are accessible");
                return;
            }

            // If there is more than one possible website and there is currently no website selected, display site list
            Site site=null;
            String path=null;
            if(sites.size()>1) {
                // More than one choice, allow user to select which one

                // Parse values
                boolean displayChoice=true;
                String url=request.getPathInfo();
                if(
                    url!=null && url.length()>0
                ) {
                    if(url.charAt(0)=='/') url=url.substring(1);
                    int pos=url.indexOf('/');
                    if(pos!=-1) {
                        String selectedServer=url.substring(0, pos);
                        int pos2=url.indexOf('/', pos+1);
                        if(pos2!=-1) {
                            String selectedSiteName=url.substring(pos+1, pos2);
                            path=url.substring(pos2+1);

                            // Find matching site
                            site=null;
                            for(Site ts : sites) {
                                if(ts.getName().equals(selectedSiteName) && ts.getLinuxServer().getHostname().toString().equals(selectedServer)) {
                                    site=ts;
                                    break;
                                }
                            }
                            if(site==null) {
                                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unable to find Site: "+selectedSiteName+" on " + selectedServer);
                                return;
                            }
                            displayChoice=false;
                        }
                    }
                }
                if(displayChoice) {
                    response.setContentType("text/html"); // TODO: ContentType constant
                    PrintWriter out = response.getWriter(); // TODO: ao-fluent-html
					// TODO: html 5 (see admin project)
                    out.print("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
                            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\" xml:lang=\"en\">\n"
                            + "  <head><title>AWStats - Select Website</title></head>\n"
                            + "  <body>\n"
                            + "    <h2>Please select a website</h2>\n"
					// TODO: ao-grid from ao-styles project
                            + "    <table cellspacing='2' cellpadding='0'>\n"
                            + "      <tr><th>Server</th><th>Primary Hostname</th><th>Directory</th></tr>\n");
                    for(Site hs : sites) {
                        DomainName hostname = hs.getLinuxServer().getHostname();
                        out.print("      <tr>\n"
                                + "        <td>");
						encodeTextInXhtml(hostname.toString(), out);
						out.print("</td>\n"
                                + "        <td>");
						encodeTextInXhtml(hs.getPrimaryHttpdSiteURL().getHostname().toString(), out);
						out.print("</td>\n"
                                + "        <td><a href='");
						// TODO: response.encodeURL, and prefix contextPath for portability
						encodeTextInXhtmlAttribute(hostname.toString(), out);
						out.print('/');
						encodeTextInXhtmlAttribute(hs.getName(), out);
						out.print("/awstats.pl'>/www/");
						encodeTextInXhtml(hs.getName(), out);
						out.print("</a></td>\n"
                                + "      </tr>\n");
                    }
                    out.print("    </table>\n"
                            + "  </body>\n"
                            + "</html>\n");
                    return;
                }
            } else {
                site=sites.get(0);
                path=request.getPathInfo();
            }

            if(path==null) {
				assert site == null;
                response.sendRedirect(request.getRequestURL().append('/').toString());
            } else {
				assert site != null;
                // Strip beginning slashes
                while(path.length()>0 && path.charAt(0)=='/') path=path.substring(1);
                if(path.length()==0) path="awstats.pl";

                String queryString=request.getQueryString();

                // Determine content type
                String contentType;
				Charset charset;
                if(path.endsWith(".pl")) {
					contentType="text/html";
					charset = StandardCharsets.UTF_8;
				}
                //else if(path.endsWith(".gif")) contentType="image/gif";
                else if(path.endsWith(".png")) {
					contentType="image/png";
					charset = null;
				} else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported file extension in path");
                    return;
                }

                // Perform proxy
                response.setContentType(contentType);
				if(charset != null) response.setCharacterEncoding(charset.name());
				try (OutputStream out = response.getOutputStream()) {
					site.getAWStatsFile(path, queryString, out);
				}
            }
        } catch(SQLException err) {
            throw new ServletException(err);
        }
    }
}
