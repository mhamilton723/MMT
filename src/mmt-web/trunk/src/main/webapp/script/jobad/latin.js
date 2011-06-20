/**
 * The LATIN implementation of the JOBAD service class and functions
 * @author: Florian Rabe, based on previous code by Catalin David
 */

// scheme + authority of the server
var catalog;
// notation style, null if none
var notstyle = 'foundations/lf/mathml.omdoc?mathml';  // hard-coding a default style for LF content

function setStyle(style) {
   notstyle = style;
   $('#currentstyle').text(style);
}
/**
 * adaptMMTURI - convert MMTURI to URL using current catalog and possibly notation style
 * act: String: action to call on MMTURI
 * present: Boolean: add presentation to action
 */
function adaptMMTURI(uri, act, present){
	var arr = uri.split("?");
	var doc = (arr.length >= 1) ? arr[0] : "";
	var mod = (arr.length >= 2) ? arr[1] : "";
	var sym = (arr.length >= 3) ? arr[2] : "";
	if (present && notstyle !== null)
	   var pres = "_present_" + notstyle;
	else
	   var pres = '';
	return catalog + '/:mmt?' + doc + '?' + mod + '?' + sym + '?' + act + pres;
}

function load(elem) {
   var url = adaptMMTURI(elem.getAttribute('jobad:load'), '', true);
   var res = null;
   function cont(data) {res = data;}
   proxyAjax('get', url, '', cont, false, 'text/xml');
   elem.removeAttribute('jobad:load');
   return res.firstChild;
}
function includeClick(elem) {
   var container = $(elem).children('div.included-module')[0];
   if (elem.hasAttribute('jobad:load')) {
      var m = load(elem);
      container.appendChild(m);
      $(container).toggle();
   } else {
      $(container).toggle();
   }
}
function remoteClick(elem) {
   var ref = load(elem);
   $(elem).replaceWith(ref);
}

function ajaxReplaceIn(url, targetid) {
   function cont(data) {
         var targetnode = $('#' + targetid).children('div');
         targetnode.replaceWith(data.firstChild);
   }
   $.ajax({ 'url': url,
            'dataType': 'xml',
            'success': cont
        });
}
function latin_navigate(uri) {
		// main div
		var url = adaptMMTURI(uri, '', true);
		ajaxReplaceIn(url, 'main');
		// cross references
		/* var refurl = catalog + '/:query/incoming?' + uri;
		ajaxReplaceIn(refurl, 'crossrefs');
      $('#crossrefs').jstree({
              "core" : {"animation": 0},
              "themes" : {"theme" : "classic", "icons" : false},
              "plugins" : ["html_data", "themes", "ui", "hotkeys"]
      }); */
		// breadcrumbs
		var bcurl = catalog + '/:breadcrumbs?' + uri;
		ajaxReplaceIn(bcurl, 'breadcrumbs');
}

function browserInit() {
   // path (; at end of path is ignored; maybe use window.location.href and parse properly)
   var path = window.location.pathname;
   // query
   var query = window.location.search.substring(1);
   // will be the requested MMT URI
   var uri = null;
   catalog = window.location.protocol + '//' + window.location.host;
	var i = query.indexOf('present_', 0);
	if (i !== -1) {
      uri = query.substring(0,i-1);
	   setStyle(query.substring(i+8));
	} else {
	   uri = query;
	   setStyle(notstyle);
   }
   if ((path.indexOf("/xhtml") != 0) && (path !== "/;") && (path !== "/")) {
      uri = path + '?' + uri;
   }
   latin_navigate(uri);
}

/**
 * parses and evaluates the formula F inside a jobad:conditional attribute
 * F ::= and(F,F) | or(F,F) | not(F) | p = V | p < V | p > V  
 * @param {Object} str - F (string)
 * @param {Object} arr - enviroment mapping symbols p to values V (boolean, string, or integer)
 */
function cond_parse(str, arr){
	if (str.substr(0, 3) == "and") {
		var p = str.substring(4, str.length - 1);
		var bracket = 0, poz = 0;
		for (var i = 0; i < p.length; i++) {
			if (p.charAt(i) == '(') 
				bracket++;
			else 
				if (p.charAt(i) == ')') 
					bracket--;
				else 
					if (p.charAt(i) == ',' && bracket == 0) {
						poz = i;
						break;
					}
		}
		return (cond_parse(p.substring(0, poz), arr) && cond_parse(p.substring(poz + 1), arr));
	}
	else 
		if (str.substr(0, 2) == "or") {
			var p = str.substring(3, str.length - 1);
			var bracket = 0, poz = 0;
			for (var i = 0; i < p.length; i++) {
				if (p.charAt(i) == '(') 
					bracket++;
				else 
					if (p.charAt(i) == ')') 
						bracket--;
					else 
						if (p.charAt(i) == ',' && bracket == 0) {
							poz = i;
							break;
						}
			}
			return (cond_parse(p.substring(0, poz), arr) || cond_parse(p.substring(poz + 1), arr));
		}
		else 
			if (str.substr(0, 3) == "not") {
				var p = str.substring(4, str.length - 1);
				return (!(cond_parse(p, arr)));
			}
			else 
				if (str.substr(0, 4).toLowerCase() == "true") {
					return true;
				}
				else 
					if (str.substr(0, 5).toLowerCase() == "false") {
						return false;
					}
					else
					   // atomic formulas
						if (str.indexOf('=') != -1) {
							var prop = str.split('=')[0];
							var val = str.split('=')[1];
							if (arr[prop] == null) 
								return false; // undefined formulas go to false, should log a warning here
							else 
								if (arr[prop] == val) 
									return true;
								else 
									return false;
						}
						else 
							if (str.indexOf('<') != -1) {
								var prop = str.split('<')[0];
								var val = str.split('<')[1];
								if (arr[prop] == null) 
									return false;
								else 
									if (arr[prop] < val) 
										return true;
									else 
										return false;
							}
							else 
								if (str.indexOf('>') != -1) {
									//same as before
									var prop = str.split('>')[0];
									var val = str.split('>')[1];
									if (arr[prop] == null) 
										return false;
									else 
										if (arr[prop] > val) 
											return true;
										else 
											return false;
								}
}

/**
 * evaluateDocument - evaluates all conditions under the target node
 * @param {Object} target - the element from which to start the parsing
 */
function evaluateDocument(target){
	//add an argument to function to make document not default
	var result = document.evaluate('.//*[@jobad:conditional]', target, nsResolver, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null); //TODO modify here to support m: define in nsResolver a h:"html namespace"... select here only html fragments which have jobad:conditional -- then handle math display lower
	for (var i = 0; i < result.snapshotLength; i++) {
		var res = result.snapshotItem(i);

		if (!checkMathMLEncapsulation('math', res)) {
			//for each of the elements with jobad:conditional
			//find the parents that have a jobad:property
			var arr = new Array();

			while (res != null && res.parentNode != document.body) {
				var tmp = res.parentNode;
				if (tmp != null && tmp.hasAttribute('jobad:property')) {
					var b = tmp.attributes['jobad:property'].value.split(':')[0];
					if (arr[b] == null) {
						arr[b] = tmp.attributes['jobad:value'].value;
					}
				}
				res = tmp;
			}
			//and parse the conditional string
			var visible = cond_parse(result.snapshotItem(i).attributes['jobad:conditional'].value, arr);
			if (visible == false) {
				//remove all children of the current node from the result
				result.snapshotItem(i).setAttribute("style", "display:none");
			}
			else {
				if (result.snapshotItem(i).hasAttribute('style')) 
					result.snapshotItem(i).removeAttribute('style');
			}
		}
		else {
			//is child of maction?
			if (getTagName(res.parentNode) != 'maction') {
				//if not, wrap in maction @actiontype=conditional and select it.
				createMactionElement(null, 'conditional', res);
			}
			//if yes, do nothing

		}
	}
	//TODO handle math <maction> here
	var result = document.evaluate('.//mathml:maction[@actiontype="conditional"] | .[@actiontype="conditional"]', target, nsResolver, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
	var i = 0;
	for (i = 0; i < result.snapshotLength; i++) {
		//for each <maction type="conditional" select="i">
		//check the children in order to see which is the first one that evaluates to true and select it
		var res1 = result.snapshotItem(i);
		var ok = false;
		for (var j = 0; j < res1.childNodes.length; j++) {
			var res = res1.childNodes[j];
			if (res.hasAttribute('jobad:conditional')) {
				//for each of the children that has a jobad:conditional
				//find the parents that have a jobad:property
				var arr = new Array();
				while (res.parentNode != document.body) {
					var tmp = res.parentNode;
					if (tmp.hasAttribute('jobad:property')) {
						var b = tmp.attributes['jobad:property'].value.split(':')[0];
						if (arr[b] == null) {
							arr[b] = tmp.attributes['jobad:value'].value;
						}
					}
					res = tmp;
				}
				var visible = cond_parse(result.snapshotItem(i).childNodes[j].attributes['jobad:conditional'].value, arr);
				if (visible) {
					ok = true;
					break;
				}
			}
			else {
				//res does not have a jobad:conditional, therefore it is always visible
				ok = true;
				break;
			}
		}
		//if no child evaluates to true, add an <mspace/> and select this one
		if (!ok) {
			var ms = document.createElementNS(NS_MATHML, 'mspace');
			res1.appendChild(ms);
		}
		res1.attributes['selection'].value = j + 1;
	}

	//handle bracket elision: find mfenced with jobad:brackets 
	var result = document.evaluate('.//mathml:mfenced[@jobad:brackets]', target, nsResolver, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
	for (var i = 0; i < result.snapshotLength; i++) {
		var res = result.snapshotItem(i);
		var arr = new Array();
		while (res != null && res.parentNode != document.body) {
			var tmp = res.parentNode;
			if (tmp != null && tmp.hasAttribute('jobad:property')) {
				var b = tmp.attributes['jobad:property'].value.split(':')[0];
				if (arr[b] == null) {
					arr[b] = tmp.attributes['jobad:value'].value;
				}
			}
			res = tmp;
		}
		//and parse the conditional string
		var visible = cond_parse(result.snapshotItem(i).attributes['jobad:brackets'].value, arr);
		res = result.snapshotItem(i);
		if (visible == false) {
			//brackets should not be visible
			//if they were visible, make them invisible
			//otherwise do nothing
			if (res.attributes['open'].value != "") {
				res.setAttribute('jobad:open', res.attributes['open'].value);
				res.setAttribute('open', '');
			}
			if (res.attributes['close'].value != "") {
				res.setAttribute('jobad:close', res.attributes['close'].value);
				res.setAttribute('close', '');
			}
		}
		else {
			//brackets are visible
			//if they were invisible, make them visible
			//otherwise do nothing
			if (res.attributes['jobad:open'].value != "") {
				res.setAttribute('open', res.attributes['jobad:open'].value);
				res.setAttribute('jobad:open', '');
			}
			if (res.attributes['jobad:close'].value != "") {
				res.setAttribute('close', res.attributes['jobad:close'].value);
				res.setAttribute('jobad:close', '');
			}
		}

	}
}

/**
 * changeCheckBox - change the value of the corresponding jobad:value of a textbox and evaluate the conditions underneath
 * @param {Object} target - the target element that has to be set the jobad:value attribute
 * @param {Object} changer - the corresponding modified checkbox
 */
function changeCheckBox(target, changer){
	target.setAttribute('jobad:value', changer.checked);
	evaluateDocument(target);
}

/**
 * changeTextBox - same as changeCheckBox, applied to a TextBox instead of CheckBox
 * @param {Object} target - the target element that has to be set the jobad:value attribute
 * @param {Object} changer - the corresponding modified textbox
 */
function changeTextBox(target, changer){
	target.setAttribute('jobad:value', changer.value);
	evaluateDocument(target);
}

var latin = clone(Service);

/**
 * Initialize the latin service
 */
latin.init = function(){
	//handle visibility
	var result = document.evaluate('//*[@jobad:property]', document, nsResolver, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
	for (var i = 0; i < result.snapshotLength; i++) {
		var tmp = result.snapshotItem(i);
		var b = tmp.attributes['jobad:property'].value.split(':')[1]; //recover the type of the property
		var span = document.createElement('span');
		var label = document.createElement('label');
		switch (b) {
		case 'boolean':
			label.setAttribute('for', escape(tmp.attributes['jobad:description'].value) + 'CheckBox');
			label.innerHTML = tmp.attributes['jobad:description'].value;
			span.appendChild(label);
			//default action - put the label + checkbox + br before the actual element				
			var newcb = document.createElement('input');
			newcb.setAttribute('type', 'checkbox');
			newcb.setAttribute('name', escape(tmp.attributes['jobad:description'].value + 'CheckBox'));
			newcb.setAttribute('onclick', 'changeCheckBox(this.parentNode.nextSibling, this)');//this.parent needs change
			if (tmp.attributes['jobad:value'].value == 'true') {
				newcb.setAttribute('checked', 'true');
			}
			else {
				//newcb.setAttribute('checked', 'false');
			}
			span.appendChild(newcb);
			var br = document.createElement('br');
			span.appendChild(br);
			//then append the current element with jobad:property to the parent
			var parent = tmp.parentNode;
			parent.insertBefore(span, tmp);
			break;
		case 'string':
			label.setAttribute('for', escape(tmp.attributes['jobad:description'].value + 'TextBox'));
			label.innerHTML = tmp.attributes['jobad:description'].value;
			span.appendChild(label);
			var newtb = document.createElement('input');
			newtb.setAttribute('type', 'text');
			newtb.setAttribute('name', escape(tmp.attributes['jobad:description'].value + 'TextBox'));
			newtb.setAttribute('onchange', 'changeTextBox(this.parentNode.nextSibling, this)');
			newtb.setAttribute('value', tmp.attributes['jobad:value'].value);
			newtb.setAttribute('size', '15');
			span.appendChild(newtb);
			var br = document.createElement('br');
			span.appendChild(br);
			//then append the current element with jobad:property to the parent
			var parent = tmp.parentNode;
			parent.insertBefore(span, tmp);
			break;
		case 'integer':
			label.setAttribute('for', escape(tmp.attributes['jobad:description'].value + 'TextBox'));
			label.innerHTML = tmp.attributes['jobad:description'].value;
			span.appendChild(label);
			var newtb = document.createElement('input');
			newtb.setAttribute('type', 'text');
			newtb.setAttribute('name', escape(tmp.attributes['jobad:description'].value + 'TextBox'));
			newtb.setAttribute('onchange', 'changeTextBox(this.parentNode.nextSibling, this)');
			newtb.setAttribute('value', tmp.attributes['jobad:value'].value);
			newtb.setAttribute('size', '5');
			span.appendChild(newtb);
			var br = document.createElement('br');
			span.appendChild(br);
			//then append the current element with jobad:property to the parent
			var parent = tmp.parentNode;
			parent.insertBefore(span, tmp);
			break;
		}
	}
	evaluateDocument(document);
	browserInit();
}

function unsetSelected(){
   $('.math-selected').removeMClass('math-selected');
}
function setSelected(target){
   unsetSelected();
   $(target).addMClass('math-selected');
}

latin.leftClick = function(target){
	//handling clicks on parts of the document - active only for elements that have jobad:href
	if (target.hasAttribute('jobad:href')) {
		var mr = $(target).closest('mrow');
		var select = (mr.length == 0) ? target : mr[0];
		setSelected(select)
		return true;
	}
	// highlight bracketed expression
	if (getTagName(target) == 'mfenced') {
		setSelected(target);
		return true;
	}
	// highlight variable declaration
	if (target.hasAttribute('jobad:varref')) {
	   var v = $(target).parents('mrow').children().filterMAttr('jobad:xref', '#' + target.getAttribute('jobad:varref'));
		setSelected(v[0]);
		return true;
	}
	unsetSelected();
	return false;
}

latin.hoverText = function(target){
	if (target.hasAttribute('jobad:href')) {
		return target.getAttribute('jobad:href');
	} else
	   return null;
}

/* currentURI is used as an auxiliary variable to communicate the MMTURI of the current symbol from the context menu entries to the methods
   this is not passed as an argument to avoid encoding problems */  
var currentURI = null;
var visibCMenu = [
		         ["hide reconstructed types", "visibHide('reconstructed')"],
		         ["show reconstructed types", "visibShow('reconstructed')"],
		         ["inherit reconstructed types", "visibInherit('reconstructed')"],
		         ["implicit arguments", "visibShow('implicit')"],
		        ];
latin.contextMenuEntries = function(target){
	if (target.hasAttribute("jobad:href")) {
		currentURI = target.getAttribute('jobad:href');
		return [
		         ["show type", "showComp('type')"],
		         ["show definition", "showComp('definition')"],
		         ["(un)mark occurrences", "showOccurs()"],
		         ["open in new window", "openCurrent()"],
		         ["show URI", "alert('" + currentURI + "')"],
		         ["get OMDoc", "openCurrentOMDoc()"],
		        ];
	} else
		return visibCMenu;
}

function visibHide(prop){
   $(focus).attr('jobad:' + prop,'false');
   evaluateDocument(focus);
}
function visibShow(prop){
   $(focus).attr('jobad:' + prop,'true');
   evaluateDocument(focus);
}
function visibInherit(prop){
   $(focus).removeAttr('jobad:' + prop);
   evaluateDocument(focus);
}

/** opens current URI in a new window as OMDoc */
function openCurrentOMDoc(){
   var url = adaptMMTURI(currentURI, 'xml', false);  
   window.open(url, '_blank', '', false);
}
/** opens current MMT URI in a new window */
function openCurrent(){
	var url = adaptMMTURI(currentURI, '', true);
	window.open(url, '_blank', '', false);
}
/** highlights all occurrences of the current URI */
function showOccurs(){
   var occs = $('mo').filterMAttr('jobad:href', currentURI).toggleMClass('math-occurrence')
}
/** shows a component of the current MMT URI in a dialog */
function showComp(comp){
	var target = adaptMMTURI(currentURI, 'component_' + comp, true);
	if(comp == 'definition')
		proxyAjax('get', target, '', continuationDef, false, 'text/xml');
	if(comp == 'type')
		proxyAjax('get', target, '', continuationType, false, 'text/xml');
}

function continuationDef (data) {	continuation(data,'definition');}
function continuationType (data) {	continuation(data,'type');}
function continuation(data, comp){
   var split = currentURI.split("?");
   var name = split[split.length - 1];
   var title = name + ((comp == 'type') ? ' : ' : ' = ');
	setLatinDialog(data.firstChild, title);	
}

/*
There are some small UI problems left to fix:
- context menu accessed from within lookup window should be on top of lookup window, currently underneath
- lookup window should not move when scrolling vertically
- title bar should be thinner
- title bar should only show the cd and name component, but not the cdbase of the symbol href (full href should be shown as @title)
 */
function setLatinDialog(content, title){
	var dia = $("#latin-dialog");
	if (dia.length == 0) {
	   dialog_init();
  	   var dia = $("#latin-dialog");
  	}
	dia.dialog('option', 'title', title);
	dia[0].replaceChild(content, dia[0].firstChild);
	dia.dialog('open');
}

function dialog_init(){
	//create and initialize the dialog
	var div = document.createElement('div');
	div.setAttribute("id", "latin-dialog");
	document.body.appendChild(div);
	var span = document.createElement('span');
	div.appendChild(span)
	$('#latin-dialog').dialog({ autoOpen: false});
}

// register service
var latinMod = ['latin', '/script/jobad/services/latin.js', latin, ""];
loadedModules.push(latinMod);

// initialize display
$(latin.init);
