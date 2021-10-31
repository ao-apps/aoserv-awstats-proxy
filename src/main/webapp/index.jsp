<%--
aoserv-awstats-proxy - Webapp that publishes AWStats reports from the AOServ Platform.
Copyright (C) 2020  AO Industries, Inc.
    support@aoindustries.com
    7262 Bull Pen Cir
    Mobile, AL 36695

This file is part of aoserv-awstats-proxy.

aoserv-awstats-proxy is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

aoserv-awstats-proxy is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License
along with aoserv-awstats-proxy.  If not, see <https://www.gnu.org/licenses/>.
--%>
<%@ page language="java" pageEncoding="UTF-8" %>
<%@ page session="false" %>
<%@ include file="/WEB-INF/taglibs.jsp" %>
<c:if test="${requestScope.pathInfo != '/'}">
	<%-- Redirect to expected listing URL --%>
	<ao:redirect statusCode="301" href="/" />
</c:if>
<ao:html>
	<head>
		<ao:meta charset="${pageContext.response.characterEncoding}" />
		<title>AWStats - Select Website</title>
		<wr:renderStyles />
	</head>
	<body>
		<h2>Please select a website</h2>
		<table class="ao-outside-border">
			<thead>
				<tr>
					<th>Server</th>
					<th>Primary Hostname</th>
					<th>Directory</th>
				</tr>
			</thead>
			<tbody>
				<c:forEach var="site" items="${requestScope.sites}">
					<c:set var="server" value="${site.linuxServer.hostname}" />
					<tr>
						<td style="white-space:nowrap">
							<c:out value="${server}" />
						</td>
						<td style="white-space:nowrap">
							<c:out value="${site.primaryHttpdSiteURL.hostname}" />
						</td>
						<td style="white-space:nowrap">
							<ao:a href="/${ao:encodeURIComponent(server)}/${ao:encodeURIComponent(site.name)}/awstats.pl">
								<c:out value="${site.installDirectory}" />
							</ao:a>
						</td>
					</tr>
				</c:forEach>
			</tbody>
		</table>
	</body>
</ao:html>
