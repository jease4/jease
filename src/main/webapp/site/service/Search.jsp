<%
String solrurl=jease.Registry.getParameter(jease.Names.JEASE_SOLR_URL,"");
if(solrurl.equals("")){//use jease result
%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.function.Predicate"%>
<%@page import="java.util.Collection"%>
<%@page import="java.util.List"%>
<%@page import="org.apache.commons.lang3.ArrayUtils"%>
<%@page import="jfix.util.I18N"%>
<%@page import="jease.Names"%>
<%@page import="jease.cms.domain.Access"%>
<%@page import="jease.cms.domain.Content"%>
<%@page import="jease.cms.domain.User"%>
<%@page import="jease.site.Authorizations"%>
<%@page import="jease.site.Fulltexts"%>
<%@page import="jease.site.HtmlSanitizer"%>
<%
  String q = request.getParameter("query");
  q = HtmlSanitizer.sanitize(q);
%>
<h1><%=I18N.get("Search_results_for")%> &quot;<%=q%>&quot;</h1>
<%
	final Collection<Access> authorizations = (Collection<Access>) session.getAttribute(Names.JEASE_SITE_AUTHORIZATIONS);
	final User user = (User) session.getAttribute(User.class.toString());
	
	List<Content> contents = new ArrayList<Content>();
	for (Content content : Fulltexts.query((Content) request.getAttribute("Root"), request.getParameter("query"))) {
		Access[] guards = Authorizations.getGuards(content);
		if (ArrayUtils.isEmpty(guards)) {
			contents.add(content);
			continue;
		}
		for (Access guard : guards) {
			if ((authorizations != null && authorizations.contains(guard)) 
					|| (user != null && guard.isDescendant(user.getRoots()))) {
				contents.add(content);
			}
		}
	}
	if (!contents.isEmpty()) {
		request.setAttribute("Pager.Scope", "search");
		request.setAttribute("Pager.Contents", contents);
		request.setAttribute("Pager.Renderer", "/site/service/pager/Searchresult.jsp");
		pageContext.include("/site/service/pager/Pager.jsp");
	} else {
%>
<p><%=I18N.get("No_results")%>.</p>
<% } %>
<%} else{ // use solr result%>
<%@include file="/site/service/Solr.jsp" %>
<%}%>