<%--
aoserv-awstats-proxy - Webapp that publishes AWStats reports from the AOServ Platform.
Copyright (C) 2020, 2021  AO Industries, Inc.
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
--%><%@ page language="java" pageEncoding="UTF-8"
%><%@ page session="false"
%><%-- JSTL 1.2
--%><%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"
%><%@ taglib prefix="x" uri="http://java.sun.com/jsp/jstl/xml"
%><%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt"
%><%@ taglib prefix="sql" uri="http://java.sun.com/jsp/jstl/sql"
%><%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions"
%><%-- AO Taglib
--%><%@ taglib prefix="ao" uri="https://oss.aoapps.com/taglib/"
%><%-- AO Web Resources Taglib
--%><%@ taglib prefix="wr" uri="https://oss.aoapps.com/web-resources/taglib/"
%>