/*
 * aoserv-awstats-proxy - Webapp that publishes AWStats reports from the AOServ Platform.
 * Copyright (C) 2006-2020, 2021, 2022  AO Industries, Inc.
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
 * along with aoserv-awstats-proxy.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.awstats_proxy;

import com.aoapps.hodgepodge.io.AOPool;
import com.aoapps.lang.io.ContentType;
import com.aoapps.lang.validation.ValidationException;
import com.aoapps.net.HostAddress;
import com.aoapps.net.Port;
import com.aoapps.net.Protocol;
import com.aoapps.servlet.http.Dispatcher;
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.SSLConnector;
import com.aoindustries.aoserv.client.TCPConnector;
import com.aoindustries.aoserv.client.account.User;
import com.aoindustries.aoserv.client.web.Site;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
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

  private static final Logger logger = Logger.getLogger(AWStatsProxy.class.getName());

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
      if (protocol.equalsIgnoreCase("ssl")) {
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
      } else if (protocol.equalsIgnoreCase("tcp")) {
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
      } else {
        throw new IllegalArgumentException("Unexpected protocol: "+protocol);
      }
    } catch (ValidationException e) {
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
      for (Site site : conn.getWeb().getSite().getRows()) {
        if (
          (site_name == null || site_name.length() == 0 || site_name.equals(site.getName()))
          && (server == null || server.length() == 0 || server.equals(site.getLinuxServer().getHostname().toString()))
        ) {
          sites.add(site);
          logger.log(Level.FINE, "DEBUG: AWStatsProxy: Found matching site: {0}", site);
        }
      }

      // If none possible, error
      if (sites.isEmpty()) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND, "No websites are accessible");
        return;
      }

      // If there is more than one possible website and there is currently no website selected, display site list
      Site site=null;
      String path=null;
      if (sites.size()>1) {
        // More than one choice, allow user to select which one

        // Parse values
        boolean displayChoice=true;
        String url=request.getPathInfo();
        if (
          url != null && url.length()>0
        ) {
          if (url.charAt(0) == '/') {
            url=url.substring(1);
          }
          int pos=url.indexOf('/');
          if (pos != -1) {
            String selectedServer=url.substring(0, pos);
            int pos2=url.indexOf('/', pos+1);
            if (pos2 != -1) {
              String selectedSiteName=url.substring(pos+1, pos2);
              path=url.substring(pos2+1);

              // Find matching site
              site=null;
              for (Site ts : sites) {
                if (ts.getName().equals(selectedSiteName) && ts.getLinuxServer().getHostname().toString().equals(selectedServer)) {
                  site=ts;
                  break;
                }
              }
              if (site == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Unable to find Site: "+selectedSiteName+" on " + selectedServer);
                return;
              }
              displayChoice=false;
            }
          }
        }
        if (displayChoice) {
          // Show site list
          request.setAttribute("pathInfo", request.getPathInfo());
          request.setAttribute("sites", sites);
          Dispatcher.forward(context, "/index.jsp", request, response);
          return;
        }
      } else {
        site=sites.get(0);
        path=request.getPathInfo();
      }

      if (path == null) {
        assert site == null;
        response.sendRedirect(request.getRequestURL().append('/').toString());
      } else {
        assert site != null;
        // Strip beginning slashes
        while (path.length()>0 && path.charAt(0) == '/') {
          path = path.substring(1);
        }
        if (path.length() == 0) {
          path="awstats.pl";
        }

        String queryString=request.getQueryString();

        // Determine content type
        String contentType;
        Charset charset;
        if (path.endsWith(".pl")) {
          contentType = ContentType.HTML;
          charset = StandardCharsets.UTF_8;
        //} else if (path.endsWith(".gif")) {
        //  contentType=ContentType.GIF;
        } else if (path.endsWith(".png")) {
          contentType = ContentType.PNG;
          charset = null;
        } else {
          response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported file extension in path");
          return;
        }

        // Perform proxy
        response.setContentType(contentType);
        if (charset != null) {
          response.setCharacterEncoding(charset.name());
        }
        try (OutputStream out = response.getOutputStream()) {
          site.getAWStatsFile(path, queryString, out);
        }
      }
    } catch (SQLException err) {
      throw new ServletException(err);
    }
  }
}
