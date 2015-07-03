/*
 *  Copyright (C) 2010-2014 Morgner UG (haftungsbeschränkt)
 *
 *  This file is part of structr <http://structr.org>.
 *
 *  structr is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  structr is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with structr.  If not, see <http://www.gnu.org/licenses/>.
 */

var win = $(window);
var engine, mode, colors = [], color = 0;
var nodeIds = [], relIds = [];
var activeTabRightGraphKey = 'structrActiveTabRightGraph_' + port;
var activeTabLeftGraphKey = 'structrActiveTabLeftGraph_' + port;
var activeTabLeftGraph, activeTabRightGraph;
var queriesSlideout, displaySlideout, filtersSlideout, nodesSlideout, relationshipsSlideout, graph;
var savedQueriesKey = 'structrSavedQueries_' + port;
var relTypes = {}, nodeTypes = {}, nodeColors = {}, relColors = {}, hasDragged, hasDoubleClicked, clickTimeout, doubleClickTime = 250, refreshTimeout;
var hiddenNodeTypes = [], hiddenRelTypes = []; //['OWNS', 'SECURITY'];
var edgeType = 'curvedArrow';
var schemaNodes = {}, schemaRelationships = {}, schemaNodesById = {};

var maxRels = 100, defaultNodeColor = '#a5a5a5', defaultRelColor = '#cccccc';
var tmpX, tmpY;
var forceAtlas2Config = {
	gravity: 1,
	strongGravityMode: true,
	adjustSizes: true,
	iterationsPerRender: 10,
	barnesHutOptimize: false,
	slowDown: 2
			//outboundAttractionDistribution: true
			//startingIterations: 1000
};

$(document).ready(function() {
	Structr.registerModule('graph', _Graph);
});

var _Graph = {
	icon: 'icon/page.png',
	add_icon: 'icon/page_add.png',
	delete_icon: 'icon/page_delete.png',
	clone_icon: 'icon/page_copy.png',
	init: function() {

		// Colors created with http://paletton.com

		colors.push('#82CE25');
		colors.push('#1DA353');
		colors.push('#E24C29');
		colors.push('#C22363');

		colors.push('#B7ED74');
		colors.push('#61C68A');
		colors.push('#FF967D');
		colors.push('#E26F9E');

		colors.push('#9BDD4A');
		colors.push('#3BAF6A');
		colors.push('#F37052');
		colors.push('#D1467E');

		colors.push('#63A80F');
		colors.push('#0C853D');
		colors.push('#B93111');
		colors.push('#9E0E48');

		colors.push('#498500');
		colors.push('#00692A');
		colors.push('#921C00');
		colors.push('#7D0033');

		colors.push('#019097');
		colors.push('#103BA8');
		colors.push('#FAA800');
		colors.push('#FA7300');

		colors.push('#3FB0B5');
		colors.push('#5070C1');
		colors.push('#FFC857');
		colors.push('#FFA557');

		colors.push('#1CA2A8');
		colors.push('#2E55B7');
		colors.push('#FFB929');
		colors.push('#FF8C29');

		colors.push('#017277');
		colors.push('#0B2E85');
		colors.push('#C68500');
		colors.push('#C65B00');

		colors.push('#00595D');
		colors.push('#072368');
		colors.push('#9A6800');
		colors.push('#9A4700');

		for (i = 50; i < 999; i++) {
			var color = 'rgb(' + (Math.floor((256 - 199) * Math.random()) + 200) + ',' + (Math.floor((256 - 199) * Math.random()) + 200) + ',' + (Math.floor((256 - 199) * Math.random()) + 200) + ')';
			colors.push(color);
		}

		_Graph.updateNodeTypes();

		sigma.renderers.def = sigma.renderers.canvas;

		if (engine) {
			//console.log('sigma engine already exists', engine);
			_Graph.scheduleRefreshEngine();
			return;
		}
		engine = new sigma({
			container: 'graph-canvas',
			settings: {
				font: 'Open Sans',
				immutable: false,
				//scalingMode: 'outside',
				//autoRescale: false,
				//batchEdgesDrawing: true,
				minNodeSize: 20,
				maxNodeSize: 20,
				borderSize: 4,
				defaultNodeBorderColor: '#a5a5a5',
				singleHover: true,
				doubleClickEnabled: false, // catch doubleClick event, see https://github.com/jacomyal/sigma.js/commit/1453afbf0d08fb5a64a88df3c3b941a2894a713b

				minEdgeSize: 4,
				maxEdgeSize: 4,
				enableEdgeHovering: true,
				edgeHoverColor: 'default',
				edgeHoverSizeRatio: 1.3,
				edgeHoverExtremities: true,
				defaultEdgeHoverColor: '#888',
				//edgeLabelSize: 'proportional',
				minArrowSize: 12
						//sideMargin: 1,
			}
		});

		engine.bind('doubleClickNode', function(e) {
			window.clearTimeout(clickTimeout);
			hasDoubleClicked = true;
			_Graph.loadRelationships(e.data.node.id);
			engine.renderers[0].dispatchEvent('outNode', {node: e.data.node});
			return false;
		});

		$(document.body).on('mousedown', function(e) {
			tmpX = e.clientX;
			tmpY = e.clientY;
		});

		$(document.body).on('mouseup', function(e) {
			hasDragged = (tmpX && tmpY && (tmpX !== e.clientX || tmpY !== e.clientY));
			tmpX = e.clientX;
			tmpY = e.clientY;
		});

		engine.bind('clickNode', function(e) {

			log('clickNode');

			if (hasDoubleClicked) {
				log('double clicked, returning');
				return false;
			}

			var node = e.data.node;

			if (hasDragged) {
				hasDragged = false;
				return false;
			}

			clickTimeout = window.setTimeout(function() {
				if (!hasDoubleClicked && !hasDragged) {
					_Entities.showProperties(node);
					engine.renderers[0].dispatchEvent('outNode', {node: node});
					window.clearTimeout(clickTimeout);
				}
			}, doubleClickTime);
			window.setTimeout(function() {
				hasDoubleClicked = false;
			}, doubleClickTime + 10);

		});

		var dragListener = new sigma.plugins.dragNodes(engine, engine.renderers[0]);
		dragListener.bind('startdrag', function() {
			hasDragged = false;
		});
		dragListener.bind('drag', function(e) {
			hasDragged = true;
			var node = e.data.node;

			engine.graph.nodes().forEach(function(n) {
				if (n === node) {
					return;
				}
				var d = _Graph.distance(node, n);

				if (shiftKey && d < 200) {

					var sourceSchemaNode = schemaNodes[node.type];
					if (!sourceSchemaNode) {
						return;
					}
					sourceSchemaNode.relatedTo.forEach(function(toRel) {

						var edgeId = node.id + '-[:' + toRel.relationshipType + ']->' + n.id;
						var possibleTargetType = schemaNodesById[toRel.targetId].name;
						if (possibleTargetType === n.type && !engine.graph.edges(edgeId)) {

							var hiddenEdge;

							if (toRel.sourceMultiplicity === '1') {
								engine.graph.edges().forEach(function(edge) {
									log(edge);
									if (edge.target === n.id && edge.relType === toRel.relationshipType) {
										edge.hidden = true;
										//console.log('outgoing rel found, will be removed', edge);
										hiddenEdge = edge.id;
									}
								});
							}
							engine.graph.addEdge({
								id: edgeId,
								label: toRel.relationshipType,
								source: node.id,
								target: n.id,
								size: 40,
								color: '#81ce25',
								type: 'curvedArrow',
								added: true,
								replaced: hiddenEdge,
								relType: toRel.relationshipType
							});

						}
					});

					sourceSchemaNode.relatedFrom.forEach(function(fromRel) {

						var edgeId = node.id + '<-[:' + fromRel.relationshipType + ']-' + n.id;
						var possibleSourceType = schemaNodesById[fromRel.sourceId].name;
						if (possibleSourceType === n.type && !engine.graph.edges(edgeId)) {

							var hiddenEdge;

							if (fromRel.sourceMultiplicity === '1') {
								engine.graph.edges().forEach(function(edge) {
									log(edge);
									if (edge.target === node.id && edge.relType === fromRel.relationshipType) {
										edge.hidden = true;
										//console.log('outgoing rel found, will be removed', edge);
										hiddenEdge = edge.id;
									}
								});
							}

							engine.graph.addEdge({
								id: edgeId,
								label: fromRel.relationshipType,
								source: n.id,
								target: node.id,
								size: 40,
								color: '#81ce25',
								type: 'curvedArrow',
								added: true,
								replaced: hiddenEdge,
								relType: fromRel.relationshipType
							});
						}
					});

					_Graph.scheduleRefreshEngine();

				} else {

					engine.graph.edges().forEach(function(edge) {

						if ((edge.source === node.id && edge.target === n.id) || (edge.source === n.id && edge.target === node.id)) {

							if (edge.added) {
								var replaced = edge.replaced;
								log('edge replaced ', replaced);
								engine.graph.dropEdge(edge.id);
								if (replaced && engine.graph.edges(replaced)) {
									engine.graph.edges(replaced).hidden = false;
								}
							}
						}
					});
				}
			});

		});
		dragListener.bind('dragend', function(e) {
			engine.graph.edges().forEach(function(edge) {

				if (edge.removed) {
					Command.deleteRelationship(edge.id, function() {
						engine.graph.dropEdge(edge.id);
					});
				}

				if (edge.added) {
					// Create new relationship
					var relData = {
						sourceId: edge.source,
						targetId: edge.target,
						relType: edge.relType
					};
					Command.createRelationship(relData, function(rel) {
						edge.color = defaultRelColor;
						edge.id = rel.id;
						edge.added = false;
						_Graph.scheduleRefreshEngine();
					});
				}
			});
		});

		engine.bind('clickEdge', function(e) {
			hasDoubleClicked = false;
			_Entities.showProperties(e.data.edge);
			engine.renderers[0].dispatchEvent('outEdge', {edge: e.data.edge});
		});

	},
	onload: function() {

		$('#main-help a').attr('href', 'http://docs.structr.org/frontend-user-guide#Graph');

		activeTabLeftGraph = localStorage.getItem(activeTabRightGraphKey);
		activeTabRightGraph = localStorage.getItem(activeTabLeftGraphKey);

		if ($('#graph-box') && $('#graph-box').length) {

			$('#graph-box').show();
			_Graph.resize();

		} else {

			main.prepend(
					'<div id="graph-box"><div id="queries" class="slideOut slideOutLeft"><div class="compTab" id="queriesTab">Queries</div><div><button id="clear-graph">Clear Graph</button></div></div>'
					+ '<div id="display" class="slideOut slideOutLeft"><div class="compTab" id="displayTab">Display Options</div></div>'
					+ '<div id="filters" class="slideOut slideOutLeft"><div class="compTab" id="filtersTab">Filters</div><div id="nodeFilters"><h3>Node Filters</h3></div><div id="relFilters"><h3>Relationship Filters</h3></div></div>'
					+ ' <div class="canvas" id="graph-canvas"></div>'
					+ ' <div id="node-types" class="graph-object-types"> <button id="show-all-node-types">Show all</button></div>'
					+ ' <div id="relationship-types" class="graph-object-types"></div>'
					//+ '<div id="nodes" class="slideOut slideOutRight"><div class="compTab" id="nodesTab">Nodes</div></div>'
					//+ '<div id="relationships" class="slideOut slideOutRight"><div class="compTab" id="relationshipsTab">Relationships</div></div>'
					+ '</div>'
					);

			queriesSlideout = $('#queries');
			displaySlideout = $('#display');
			filtersSlideout = $('#filters');

			graph = $('#graph-canvas');

			$(document.body).on('selectstart', function(e) {
				e.preventDefault();
				return false;
			});

			graph.droppable({
				accept: '.node-type',
				drop: function(e, ui) {
					var nodeType = ui.helper.text();
					var x = ui.offset.left;
					var y = ui.offset.top;
					//console.log('Creating node of type', nodeType, x, y);
					Command.create({
						type: nodeType
					}, function(obj) {

						Command.get(obj.id, function(node) {
							_Graph.drawNode(node);
							_Graph.refreshEngine();
						});

					});

				}
			});

			_Graph.init();

			nodesSlideout = $('#nodes');
			relationshipsSlideout = $('#relationships');

			lsw = queriesSlideout.width() + 12;
			rsw = nodesSlideout.width() + 12;

			$('.slideOut').on('mouseover', function() {
				running = false;
				return true;
			});

			$('.slideOut').on('mouseout', function() {
				running = true;
				//_Graph.scheduleRefreshEngine();
				return true;
			});

			$('#queriesTab').on('click', function() {
				if (Math.abs(queriesSlideout.position().left + lsw) <= 3) {
					Structr.closeLeftSlideOuts([displaySlideout, filtersSlideout], activeTabLeftGraphKey);
					Structr.openLeftSlideOut(queriesSlideout, this, activeTabLeftGraphKey);
				} else {
					Structr.closeLeftSlideOuts([queriesSlideout], activeTabLeftGraphKey);
				}
			});

			$('#displayTab').on('click', function() {
				if (Math.abs(displaySlideout.position().left + lsw) <= 3) {
					Structr.closeLeftSlideOuts([queriesSlideout, filtersSlideout], activeTabLeftGraphKey);
					Structr.openLeftSlideOut(displaySlideout, this, activeTabLeftGraphKey, function() {
						//console.log('Display options opened');
					});
				} else {
					Structr.closeLeftSlideOuts([displaySlideout], activeTabLeftGraphKey);
				}
			});

			$('#filtersTab').on('click', function() {
				if (Math.abs(filtersSlideout.position().left + lsw) <= 3) {
					Structr.closeLeftSlideOuts([queriesSlideout, displaySlideout], activeTabLeftGraphKey);
					Structr.openLeftSlideOut(filtersSlideout, this, activeTabLeftGraphKey, function() {
						//console.log('Filters opened');
					});
				} else {
					Structr.closeLeftSlideOuts([filtersSlideout], activeTabLeftGraphKey);
				}
			});

//        $('#nodesTab').on('click', function() {
//            if (nodesSlideout.position().left === $(window).width()) {
//                Structr.closeSlideOuts([relationshipsSlideout], activeTabRightGraphKey);
//                Structr.openSlideOut(nodesSlideout, this, activeTabRightGraphKey, function() {
//                    console.log('Nodes opened');
//                });
//            } else {
//                Structr.closeSlideOuts([nodesSlideout], activeTabRightGraphKey);
//            }
//        });
//
//        $('#relationshipsTab').on('click', function() {
//            if (relationshipsSlideout.position().left === $(window).width()) {
//                Structr.closeSlideOuts([nodesSlideout], activeTabRightGraphKey);
//                Structr.openSlideOut(relationshipsSlideout, this, activeTabRightGraphKey, function() {
//                    console.log('Rels opened');
//                });
//            } else {
//                Structr.closeSlideOuts([relationshipsSlideout], activeTabRightGraphKey);
//            }
//        });

			if (activeTabLeftGraph) {
				$('#' + activeTabLeftGraph).addClass('active').click();
			}

			if (activeTabRightGraph) {
				$('#' + activeTabRightGraph).addClass('active').click();
			}

			queriesSlideout.append('<div class="query-box"><textarea class="search" name="rest" cols="39" rows="4" placeholder="Enter a REST query here"></textarea><img class="clearSearchIcon" id="clear-rest" src="icon/cross_small_grey.png">'
					+ '<button id="exec-rest">Execute REST query</button></div>');

			queriesSlideout.append('<div class="query-box"><textarea class="search" name="cypher" cols="39" rows="4" placeholder="Enter a Cypher query here"></textarea><img class="clearSearchIcon" id="clear-cypher" src="icon/cross_small_grey.png">'
					+ '<button id="exec-cypher">Execute Cypher query</button></div>');

			queriesSlideout.append('<div id="cypher-params"><h3>Cypher Parameters</h3><img id="add-cypher-parameter" src="icon/add.png">');
			_Graph.appendCypherParameter($('#cypher-params'));

			$('#clear-graph').on('click', function() {
				_Graph.clearGraph();
			});

			$('#exec-rest').on('click', function() {
				var query = $('.search[name=rest]').val();
				if (query && query.length) {
					_Graph.execQuery(query, 'rest');
				}
			});

			$('#exec-cypher').on('click', function() {
				var query = $('.search[name=cypher]').val();
				var params = {};
				var names = $.map($('[name="cyphername[]"]'), function(n) {
					return $(n).val();
				});
				var values = $.map($('[name="cyphervalue[]"]'), function(v) {
					return $(v).val();
				});

				for (var i = 0; i < names.length; i++) {
					params[names[i]] = values[i];
				}

				if (query && query.length) {
					_Graph.execQuery(query, 'cypher', JSON.stringify(params));
				}
			});

			$('#add-cypher-parameter').on('click', function() {
				_Graph.appendCypherParameter($('#cypher-params'));
			});

			_Graph.activateClearSearchIcon();

			queriesSlideout.append('<div><h3>Saved Queries</h3></div>');
			_Graph.listSavedQueries();

			//_Graph.restoreSavedQuery(0);

			searchField = $('.search', queriesSlideout);
			searchField.focus();
			searchField.keydown(function(e) {
				var rawSearchString = $(this).val();
				var searchString = rawSearchString;

				var self = $(this);
				var type = self.attr('name');
				if (type !== 'cypher') {

					var type;
					var posOfColon = rawSearchString.indexOf(':');
					if (posOfColon > -1) {
						type = rawSearchString.substring(0, posOfColon);
						type = type.capitalize();
						searchString = rawSearchString.substring(posOfColon + 1, rawSearchString.length);
					}
				}
				if (searchString && searchString.length) {
					_Graph.activateClearSearchIcon(type);
				} else {
					_Graph.clearSearch(type);
				}

				if (searchString && searchString.length && e.which === 13) {
					//console.log('Search executed', searchString, type);

					if (!shiftKey) {
						_Graph.execQuery(searchString, type);
						return false;
					}

				} else if (e.which === 27 || rawSearchString === '') {
					_Graph.clearSearch(type);
				}
			});

		}

		win.off('resize');
		win.resize(function() {
			_Graph.resize();
		});

		// Wait 1 second before releasing the main menu
		window.setTimeout(function() {
			Structr.unblockMenu();
		}, 1000);

	},
	execQuery: function(query, type, params) {

		//console.log('exec', type, 'query: ', query, ', with parameters.', params);

		if (query && query.length) {

			if (type === 'cypher') {
				Command.cypher(query.replace(/(\r\n|\n|\r)/gm, ''), params);
				_Graph.saveQuery(query, 'cypher', params);
			} else {
				Command.rest(query.replace(/(\r\n|\n|\r)/gm, ''));
				_Graph.saveQuery(query, 'rest');
			}

			_Graph.listSavedQueries();

		}
	},
	saveQuery: function(query, type, params) {
		var savedQueries = JSON.parse(localStorage.getItem(savedQueriesKey)) || [];
		var exists = false;
		$.each(savedQueries, function(i, q) {
			if (q.query === query && q.params === params) {
				exists = true;
			}
		});
		if (!exists) {
			savedQueries.unshift({'type': type, 'query': query, 'params': params});
			localStorage.setItem(savedQueriesKey, JSON.stringify(savedQueries));
			Structr.saveLocalStorage();
		}
	},
	removeSavedQuery: function(i) {
		var savedQueries = JSON.parse(localStorage.getItem(savedQueriesKey)) || [];
		savedQueries.splice(i, 1);
		localStorage.setItem(savedQueriesKey, JSON.stringify(savedQueries));
		_Graph.listSavedQueries();
		Structr.saveLocalStorage();
	},
	restoreSavedQuery: function(i, exec) {
		var savedQueries = JSON.parse(localStorage.getItem(savedQueriesKey)) || [];
		var query = savedQueries[i];
		$('.search[name=' + query.type + ']').val(query.query);
		_Graph.activateClearSearchIcon(query.type);
		$('#cypher-params input').remove();
		$('#cypher-params br').remove();
		$('#cypher-params img.remove-cypher-parameter').remove();
		if (query.params && query.params.length) {
			var parObj = JSON.parse(query.params);
			$.each(Object.keys(parObj), function(i, key) {
				_Graph.appendCypherParameter($('#cypher-params'), key, parObj[key]);
			});
		} else {
			_Graph.appendCypherParameter($('#cypher-params'));
		}
		if (exec) {
			_Graph.execQuery(query.query, query.type, query.params);
		}
	},
	listSavedQueries: function() {
		$('#saved-queries').empty();
		queriesSlideout.append('<div id="saved-queries"></div>');
		var savedQueries = JSON.parse(localStorage.getItem(savedQueriesKey)) || [];
		$.each(savedQueries, function(q, query) {
			if (query.type === 'cypher') {
				$('#saved-queries').append('<div class="saved-query cypher-query"><img class="replay" alt="Cypher Query" src="icon/control_play_blue.png">' + query.query + '<img class="remove-query" src="icon/cross_small_grey.png"></div>');
			} else {
				$('#saved-queries').append('<div class="saved-query rest-query"><img class="replay" alt="REST Query" src="icon/control_play.png">' + query.query + '<img class="remove-query" src="icon/cross_small_grey.png"></div>');
			}
		});
		$('.saved-query').on('click', function() {
			_Graph.restoreSavedQuery($(this).index());
		});
		$('.replay').on('click', function() {
			_Graph.restoreSavedQuery($(this).parent().index(), true);
		});
		$('.remove-query').on('click', function() {
			_Graph.removeSavedQuery($(this).parent().index());
		});
	},
	activateClearSearchIcon: function(type) {
		var icon = $('#clear-' + type);
		icon.show().on('click', function() {
			$(this).hide();
			$('.search[name=' + type + ']').val('').focus();
		});
	},
	clearSearch: function(type) {
		$('#clear-' + type).hide().off('click');
		$('.search[name=' + type + ']').val('').focus();
	},
	clearGraph: function() {
		relTypes = {};
		nodeTypes = {};
		//nodeColors = {};
		//relColors = {};
		nodeIds = [];
		relIds = [];
		engine.graph.clear();
		engine.refresh();
	},
	loadRelationships: function(nodeId) {
		if (nodeId) {
			$.ajax({
				url: rootUrl + nodeId + '/out?pageSize=' + maxRels,
				dataType: "json",
				//async: false,
				success: function(data) {
					if (!data || data.length === 0 || !data.result || !data.result.length) {
						return;
					}
					var results = data.result;
					var count = 0, i = 0;
					while (i < results.length && count < maxRels) {
						var r = results[i++];
						if (relIds.indexOf(r.id) === -1) {
							relIds.push(r.id);
							_Graph.loadRelationship(r);
						}
					}
				}
			});

			$.ajax({
				url: rootUrl + nodeId + '/in?pageSize=' + maxRels,
				dataType: "json",
				//async: false,
				success: function(data) {
					if (!data || data.length === 0 || !data.result || !data.result.length) {
						return;
					}
					var results = data.result;
					var count = 0, i = 0;
					while (i < results.length && count < maxRels) {
						var r = results[i++];
						if (relIds.indexOf(r.id) === -1) {
							relIds.push(r.id);
							_Graph.loadRelationship(r);
						}
					}
				}
			});
		}
	},
	loadNode: function(nodeId, callback) {
		//console.log('loadNode', nodeId, callback);
		if (nodeId) {
			Command.get(nodeId, function(n) {
				_Graph.drawNode(n);
				if (callback) {
					callback();
				}
			});
		}
	},
	loadRelationship: function(rel) {
		//console.log('loadRelationship', rel);
		_Graph.loadNode(rel.sourceId, function() {
			_Graph.loadNode(rel.targetId, function() {
				_Graph.drawRel(rel);
			})
		});
	},
	findRelationships: function(sourceId, targedId, relType) {
		var edges = [];
		engine.graph.edges().forEach(function(edge) {
			if (edge.source === sourceId && edge.target === targedId && (!relType || edge.relType === relType)) {
				edges.push(edge);
			}
		});
		return edges;
	},
	drawNode: function(node, x, y) {
		if (isIn(node.id, nodeIds)) {
			return;
		}
		nodeIds.push(node.id);
		_Graph.setNodeColor(node);
		//console.log('drawing node', node, nodeTypes[node.type]);
		engine.graph.addNode({
			id: node.id || node.name,
			label: (node.name || node.tag || node.id.substring(0, 5) + '…') + ':' + node.type,
			x: x || Math.random(10),
			y: y || Math.random(10),
			size: 20,
			color: nodeColors[node.type],
			type: node.type,
			name: node.name,
			hidden: isIn(node.type, hiddenNodeTypes)
		});
		_Graph.scheduleRefreshEngine();
		//_Graph.updateNodeTypes();
	},
	drawRel: function(r) {
		relIds.push(r.id);
		_Graph.setRelationshipColor(r);
		//var existingEdges = _Graph.findRelationships(r.sourceId, r.targetId, r.relType);
		var existingEdges = _Graph.findRelationships(r.sourceId, r.targetId);
		var c = existingEdges.length * 15;
		//console.log('Found existing edges:', r, existingEdges, c);
		engine.graph.addEdge({
			id: r.id,
			label: r.relType,
			source: r.sourceId,
			target: r.targetId,
			size: 40,
			color: defaultRelColor,
			type: edgeType,
			relType: r.relType,
			hidden: isIn(r.relType, hiddenRelTypes),
			count: c
		});
		_Graph.scheduleRefreshEngine();
		_Graph.updateRelationshipTypes();
	},
	updateNode: function(node, obj) {
		if (!node) {
			node = engine.graph.nodes(obj.id);
		}
		if (obj.name) {
			node.name = obj.name;
		}
		if (obj.id) {
			node.id = obj.id;
		}
		if (obj.tag) {
			node.tag = obj.tag;
		}
		node.label = (node.name || node.tag || node.id.substring(0, 5) + '…') + ':' + node.type;
		if (obj.type) {
			node.type = obj.type;
		}

		_Graph.scheduleRefreshEngine();

	},
	resize: function() {

		Structr.resize();

		var windowHeight = win.height();
		var offsetHeight = 360;

		$('#saved-queries').css({
			height: windowHeight - offsetHeight + 'px'
		});

		var ch = win.height() - 61;

		graph.css({
			height: ch,
			width: win.width(),
		});

		$('canvas', graph).css({
			height: ch,
			width: win.width(),
		});

		$('#relationship-types').css({
			top: $('#node-types').height() + $('#node-types').position().top + 64
		});

		if (engine) {
			_Graph.scheduleRefreshEngine();
		}

	},
	loadTypeDefinition: function(type, callback) {
		var url = rootUrl + '_schema/' + type;
		$.ajax({
			url: url,
			dataType: 'json',
			contentType: 'application/json; charset=utf-8',
			//async: false,
			statusCode: {
				200: function(data) {
					if (callback) {
						callback(data.result[0]);
					}
				},
				401: function(data) {
					console.log(data);
				},
				404: function(data) {
					console.log(data);
				},
				422: function(data) {
					console.log(data);
				}
			}

		}).always(function(data) {
			if (callback) {
				callback(data.result[0]);
			}
		});
	},
	updateNodeTypes: function() {

		var nodeTypesBox = $('#node-types');
		nodeTypesBox.empty();
		//nodeTypesBox.append('<button id="show-all-node-types">Show all</button>');
		Command.getByType('SchemaNode', 1000, 1, null, null, null, true, function(node) {

			schemaNodes[node.name] = node;
			schemaNodesById[node.id] = node;

			var nodeType = node.name;

			if (!isIn(nodeType, Object.keys(nodeColors))) {
				nodeColors[nodeType] = colors[color++]
			}

			//Object.keys(nodeColors).forEach(function (nodeType) {
			nodeTypesBox.append('<div id="node-type-' + nodeType + '" class="node-type"><div class="circle" style="background-color: ' + nodeColors[nodeType] + '"></div>' + nodeType + '</div>');
			var nt = $('#node-type-' + nodeType, nodeTypesBox);
			if (isIn(nodeType, hiddenNodeTypes)) {
				nt.attr('data-hidden', 1);
				nt.addClass('hidden-node-type');
				//console.log('nodeType is hidden', nodeType);
			}
			nt.on('mousedown', function() {
				var nodeTypeEl = $(this);
				nodeTypeEl.css({pointer: 'move'});
				//_Graph.toggleNodeType(nodeType);
			}).on('click', function() {
				var n = $(this);
				if (n.attr('data-hidden')) {
					_Graph.showNodeType(nodeType, function() {
						n.removeAttr('data-hidden', 1);
						n.removeClass('hidden-node-type');
					});
				} else {
					_Graph.hideNodeType(nodeType, function() {
						n.attr('data-hidden', 1);
						n.addClass('hidden-node-type');
					});
				}
			}).on('mouseover', function() {
				_Graph.highlightNodeType(nodeType);
			}).on('mouseout', function() {
				_Graph.unhighlightNodeType(nodeType);
			}).draggable({
				helper: 'clone'
			});
		});

	},
	hideNodeType: function(type, callback) {
		engine.graph.nodes().forEach(function(node) {
			if (node.type === type) {
				node.hidden = true;
			}
		});
		hiddenNodeTypes.push(type);
		_Graph.refreshEngine();
		if (callback) {
			callback();
		}
		//console.log(hiddenNodeTypes);
	},
	showNodeType: function(type, callback) {
		engine.graph.nodes().forEach(function(node) {
			if (node.type === type) {
				node.hidden = false;
			}
		});
		hiddenNodeTypes.splice(hiddenNodeTypes.indexOf('type'), 1);
		_Graph.refreshEngine();
		if (callback) {
			callback();
		}
		//console.log(hiddenNodeTypes);
	},
	highlightNodeType: function(type) {
		engine.graph.nodes().forEach(function(node) {
			if (node.type === type) {
				node.oldColor = node.color;
				node.color = colorLuminance(node.color, -.2);
			}
		});
		_Graph.scheduleRefreshEngine();
	},
	unhighlightNodeType: function(type) {
		engine.graph.nodes().forEach(function(node) {
			if (node.type === type) {
				node.color = node.oldColor;
			}
		});
		_Graph.scheduleRefreshEngine();
	},
	setNodeColor: function(node) {
		if (!isIn(node.type, Object.keys(nodeColors))) {
			node.color = colors[color++];
			//console.log(typeDef.type, typeDef.color, color);
			nodeColors[node.type] = node.color;
		} else {
			node.color = nodeColors[node.type];
		}
	},
	setRelationshipColor: function(rel) {
		//console.log('setRelColor', rel);
		if (!isIn(rel.relType, Object.keys(relColors))) {
			rel.color = colors[color++];
			//console.log(typeDef.type, typeDef.color, color);
			relColors[rel.relType] = rel.color;
		} else {
			rel.color = relColors[rel.relType];
		}
	},
	updateRelationshipTypes: function() {
		var relTypesBox = $('#relationship-types');
		relTypesBox.empty();
		//console.log(relColors);
		Object.keys(relColors).forEach(function(relType) {
			relTypesBox.append('<div id="rel-type-' + relType + '">' + relType + '</div>');
			var rt = $('#rel-type-' + relType, relTypesBox);
			if (isIn(relType, hiddenRelTypes)) {
				rt.attr('data-hidden', 1);
				rt.addClass('hidden-node-type');
			}
			rt.on('mousedown', function() {
				var relTypeEl = $(this);
				relTypeEl.css({pointer: 'move'});
				//_Graph.toggleNodeType(nodeType);
			}).on('click', function() {
				var n = $(this);
				if (n.attr('data-hidden')) {
					_Graph.showRelType(relType, function() {
						n.removeAttr('data-hidden', 1);
						n.removeClass('hidden-node-type');
					});
				} else {
					_Graph.hideRelType(relType, function() {
						n.attr('data-hidden', 1);
						n.addClass('hidden-node-type');
					});
				}
			}).on('mouseover', function() {
				_Graph.highlightRelationshipType(relType);
			}).on('mouseout', function() {
				_Graph.unhighlightRelationshipType(relType);
			});
		});

	},
	highlightRelationshipType: function(type) {
		engine.graph.edges().forEach(function(edge) {
			if (edge.relType === type) {
				edge.oldColor = edge.color;
				edge.color = colorLuminance(edge.color, -.2);
			}
		});
		_Graph.scheduleRefreshEngine();
	},
	unhighlightRelationshipType: function(type) {
		engine.graph.edges().forEach(function(edge) {
			if (edge.relType === type) {
				edge.color = edge.oldColor;
			}
		});
		_Graph.scheduleRefreshEngine();
	},
	hideRelType: function(type, callback) {
		engine.graph.edges().forEach(function(edge) {
			if (edge.relType === type) {
				edge.hidden = true;
			}
		});
		hiddenRelTypes.push(type);
		_Graph.scheduleRefreshEngine();
		if (callback) {
			callback();
		}
	},
	showRelType: function(type, callback) {
		engine.graph.edges().forEach(function(edge) {
			if (edge.relType === type) {
				edge.hidden = false;
			}
		});
		hiddenRelTypes.splice(hiddenRelTypes.indexOf('type'), 1);
		_Graph.scheduleRefreshEngine();
		if (callback) {
			callback();
		}
	},
	appendCypherParameter: function(el, key, value) {
		el.append('<div><img class="remove-cypher-parameter" src="icon/delete.png"> <input name="cyphername[]" type="text" placeholder="name" size="10" value="' + (key || '') + '"> <input name="cyphervalue[]" type="text" placeholder="value" size="10" value="' + (value || '') + '"></div>');
		$('.remove-cypher-parameter', el).on('click', function() {
			$(this).parent().remove();
		});
	},
	scheduleRefreshEngine: function() {
		window.clearTimeout(refreshTimeout);
		refreshTimeout = window.setTimeout(_Graph.refreshEngine, 100);
	},
	refreshEngine: function() {
		hasDoubleClicked = false;
		hasDragged = false;
		engine.refresh();
	},
	distance: function(n1, n2) {
		var x1 = parseInt(n1['renderer1:x']);
		var y1 = parseInt(n1['renderer1:y']);
		var x2 = parseInt(n2['renderer1:x']);
		var y2 = parseInt(n2['renderer1:y']);
		var d = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
		//console.log(x1,y2, x2,y2, d);
		return d;
	}
};

function getRandomInt(min, max) {
	return Math.floor(Math.random() * (max - min)) + min;
}

function colorLuminance(hex, lum) {

	hex = String(hex).replace(/[^0-9a-f]/gi, '');
	lum = lum || 0;
	if (hex.length < 6) {
		hex = hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2];
	}

	// convert to decimal
	var r = convertToDec(hex.substr(0, 2)), g = convertToDec(hex.substr(2, 2)), b = convertToDec(hex.substr(4, 2));

	// desaturate
	var rgb = desaturate(r, g, b, .5);

	// change luminosity

	var newHex = "#", c, i;
	for (i = 0; i < 3; i++) {
		c = rgb[i];
		c = Math.round(Math.min(Math.max(0, c + (c * lum)), 255)).toString(16);
		newHex += ("00" + c).substr(c.length);
	}
	return newHex;
}

function convertToDec(hex) {
	return parseInt(hex, 16);
}

function desaturate(r, g, b, k) {
	var intensity = 0.3 * r + 0.59 * g + 0.11 * b;
	r = Math.floor(intensity * k + r * (1 - k));
	g = Math.floor(intensity * k + g * (1 - k));
	b = Math.floor(intensity * k + b * (1 - k));
	return [r, g, b];
}