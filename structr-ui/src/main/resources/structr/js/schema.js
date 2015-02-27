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
var canvas, instance, res, nodes = [], rels = [], localStorageSuffix = '_schema_' + port, undefinedRelType = 'UNDEFINED_RELATIONSHIP_TYPE', initialRelType = undefinedRelType;
var radius = 20, stub = 30, offset = 0, maxZ = 0, reload = false;
var connectorStyle = localStorage.getItem(localStorageSuffix + 'connectorStyle') || 'Flowchart';
var remotePropertyKeys = [];

$(document).ready(function() {

    Structr.registerModule('schema', _Schema);
    Structr.classes.push('schema');
});

var _Schema = {
    type_icon: 'icon/database_table.png',
    schemaLoading: false,
    schemaLoaded: false,
    reload: function() {
        if (reload) {
            return;
        }
        reload = true;
        _Schema.storePositions();
        main.empty();
        _Schema.init();
        _Schema.resize();
    },
    storePositions: function() {
        $.each($('#schema-graph .node'), function(i, n) {
            var node = $(n);
            var id = node.attr('id');
            var obj = JSON.parse(localStorage.getItem(id + localStorageSuffix + 'node-position')) || {};
            obj.position = node.position();
            localStorage.setItem(id + localStorageSuffix + 'node-position', JSON.stringify(obj));
        });
    },
    storeMode: function(id, mode) {
        var obj = JSON.parse(localStorage.getItem(id + localStorageSuffix + 'node-position')) || {};
        obj.mode = mode;
        localStorage.setItem(id + localStorageSuffix + 'node-position', JSON.stringify(obj));
    },
    getPosition: function(id) {
        var n = JSON.parse(localStorage.getItem(id + localStorageSuffix + 'node-position'));
        return n ? n.position : undefined;
    },
    getMode: function(id) {
        var n = JSON.parse(localStorage.getItem(id + localStorageSuffix + 'node-position'));
        return n ? n.mode : 'compact';
    },
    init: function() {

        _Schema.schemaLoading = false;
        _Schema.schemaLoaded = false;
        _Schema.schema = [];
        _Schema.keys = [];

        main.append('<div class="schema-input-container"></div>');

        var schemaContainer = $('.schema-input-container');

        Structr.ensureIsAdmin(schemaContainer, function() {

            schemaContainer.append('<input class="schema-input" id="type-name" type="text" size="20" placeholder="New type"><button id="create-type" class="btn"><img src="icon/add.png"> Add Type</button>');

            schemaContainer.append('<input class="schema-input" id="ggist-url" type="text" size="30" placeholder="Enter a GraphGist raw URL"><button id="gg-import" class="btn">Start Import</button>');
            $('#gg-import').on('click', function(e) {
                var btn = $(this);
                var text = btn.text();
                btn.attr('disabled', 'disabled').addClass('disabled').html(text + ' <img src="img/al.gif">');
                e.preventDefault();
                _Schema.importGraphGist($('#ggist-url').val(), text);
            });

            var styles = ['Flowchart', 'Bezier', 'StateMachine', 'Straight'];

            schemaContainer.append('<select id="connector-style"></select>');
            $.each(styles, function(i, style) {
                $('#connector-style').append('<option value="' + style + '" ' + (style === connectorStyle ? 'selected="selected"' : '') + '>' + style + '</option>');
            });
            $('#connector-style').on('change', function() {
                var newStyle = $(this).val();
                connectorStyle = newStyle;
                localStorage.setItem(localStorageSuffix + 'connectorStyle', newStyle);
                _Schema.reload();
            });

           schemaContainer.append('<button class="btn" id="expand-all"><img alt="Expand all" src="icon/arrow_out.png"></button>');
            $('#expand-all').on('click', function() {
                $.each(Object.keys(nodes), function(i, id) {
                    if (_Schema.getMode(id) === 'compact') {
                        _Schema.expandView(id);
                        _Schema.storeMode(id, 'expanded');
                    }
                });
            });

            schemaContainer.append('<button class="btn" id="compact-all"><img alt="Compact all" src="icon/arrow_in.png"></button>');
            $('#compact-all').on('click', function() {
                $.each(Object.keys(nodes), function(i, id) {
                    if (_Schema.getMode(id) === 'expanded') {
                        _Schema.compactView(id);
                        _Schema.storeMode(id, 'compact');
                    }
                });
                instance.repaintEverything();
            });

            schemaContainer.append('<button class="btn" id="admin-tools"><img src="icon/wrench.png"> Tools</button>');
            $('#admin-tools').on('click', function() {
                _Schema.openAdminTools();
            });

            schemaContainer.append('<button class="btn" id="sync-schema"><img src="icon/page_white_get.png"> Sync schema</button>');
            $('#sync-schema').on('click', function() {
                _Schema.syncSchemaDialog();
            });

            schemaContainer.append('<button class="btn" id="save-layout"><img src="icon/database.png"> Save layout</button>');
            $('#save-layout').on('click', function() {
                Structr.saveLocalStorage();
            });

            /*
             schemaContainer.append('<button class="btn" id="do-layout"><img src="icon/wrench.png"> Layout</button>');
             $('#do-layout').on('click', function() {
             _Schema.doLayout();
             });

             schemaContainer.append('<button class="btn" id="stop-layout"><img src="icon/wrench.png"> Stop</button>');
             $('#stop-layout').on('click', function() {
             _Layout.stopLayout();
             });
             */

            $('#type-name').on('keyup', function(e) {

                if (e.keyCode === 13) {
                    e.preventDefault();
                    if ($('#type-name').val().length) {
                        $('#create-type').click();
                    }
                    return false;
                }

            });
            $('#create-type').on('click', function() {
                _Schema.createNode($('#type-name').val());
            });

            jsPlumb.ready(function() {
                main.append('<div class="canvas" id="schema-graph"></div>');

                canvas = $('#schema-graph');
                _Schema.resize();

                instance = jsPlumb.getInstance({
                    //Connector: "StateMachine",
                    PaintStyle: {
                        lineWidth: 5,
                        strokeStyle: "#81ce25"
                    },
                    Endpoint: ["Dot", {radius: 6}],
                    EndpointStyle: {
                        fillStyle: "#aaa"
                    },
                    Container: "schema-graph",
                    ConnectionOverlays: [
                        ["PlainArrow", {
                                location: 1,
                                width: 15,
                                length: 12
                            }
                        ]
                    ]
                });

                _Schema.loadSchema(function() {
                    instance.bind('connection', function(info) {
                        _Schema.connect(getIdFromPrefixIdString(info.sourceId, 'id_'), getIdFromPrefixIdString(info.targetId, 'id_'));
                    });
                    instance.bind('connectionDetached', function(info) {
                        Structr.confirmation('<h3>Delete schema relationship?</h3>',
                                function() {
                                    $.unblockUI({
                                        fadeOut: 25
                                    });
                                    _Schema.detach(info.connection.scope);
                                    _Schema.reload();
                                });
                        _Schema.reload();
                    });

                    reload = false;
                });

            });

            $(window).off('resize');
            $(window).on('resize', function() {
                _Schema.resize();
            });
        });

    },
    onload: function() {
        _Schema.init();
        $('#main-help a').attr('href', 'http://docs.structr.org/frontend-user-guide#Schema');
    },
    /**
     * Read the schema from the _schema REST resource and call 'callback'
     * after the complete schema is loaded.
     */
    loadSchema: function(callback) {
        // Avoid duplicate loading of schema
        if (_Schema.schemaLoading) {
            return;
        }
        _Schema.schemaLoading = true;

        _Schema.loadNodes(function() {
            _Schema.loadRels(callback);
        });

    },
    isSchemaLoaded: function() {
        var all = true;
        if (!_Schema.schemaLoaded) {
            $.each(_Schema.types, function(t, type) {
                all &= (_Schema.schema[type] && _Schema.schema[type] !== null);
            });
        }
        _Schema.schemaLoaded = all;
        return _Schema.schemaLoaded;
    },
    loadNodes: function(callback) {
        var url = rootUrl + 'schema_nodes';
        $.ajax({
            url: url,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            success: function(data) {

                $.each(data.result, function(i, entity) {

                    var isBuiltinType = entity.isBuiltinType;
                    var id = 'id_' + entity.id;
                    nodes[entity.id] = entity;
                    canvas.append('<div class="schema node compact'
                            + (isBuiltinType ? ' light' : '')
                            + '" id="' + id + '"><b>' + entity.name + '</b>'
                            + '<img class="icon toggle-view" src="icon/arrow_out.png">'
                            + '<img class="icon delete" src="icon/delete' + (isBuiltinType ? '_gray' : '') + '.png">'
                            + '<img class="icon edit" src="icon/pencil.png">'
                            + '</div>');


                    var node = $('#' + id);

                    if (!isBuiltinType) {
                        node.children('b').on('click', function() {
                            _Schema.makeAttrEditable(node, 'name');
                        });
                    }

                    node.on('click', function() {
                        node.css({zIndex: ++maxZ});
                    });

                    if (!isBuiltinType) {
                        node.children('.delete').on('click', function() {
                            Structr.confirmation('<h3>Delete schema node?</h3><p>This will delete all incoming and outgoing schema relationships as well, but no data will be removed.</p>',
                                    function() {
                                        $.unblockUI({
                                            fadeOut: 25
                                        });
                                        _Schema.deleteNode(entity.id);
                                    });
                        });
                    }

                    var storedPosition = _Schema.getPosition(id);
                    node.offset({
                        left: storedPosition ? storedPosition.left : i * 100 + 25,
                        top: storedPosition ? storedPosition.top : i * 40 + 131
                    });

                    $('.toggle-view', node).on('click', function() {
                        _Schema.toggleView(entity.id);
                    });

                    if (_Schema.getMode(entity.id) !== 'compact') {
                        _Schema.expandView(entity.id);
                    }

                    $('.edit', node).on('click', function(e) {
                        e.stopPropagation();
                        Structr.dialog('Edit schema node', function() {
                        }, function() {
                            instance.repaintEverything();
                        });
                        var id = getId($(this).closest('.schema.node'));

                        if (!id) {
                            return false;
                        }

                        Command.get(id, function(entity) {
                            _Schema.loadNode(entity, dialogText);
                        });

                        return false;
                    });

                    nodes[entity.id + '_top'] = instance.addEndpoint(id, {
                        //anchor: [ "Perimeter", { shape: "Square" } ],
                        anchor: "Top",
                        maxConnections: -1,
                        //isSource: true,
                        isTarget: true,
                        deleteEndpointsOnDetach: false
                    });
                    nodes[entity.id + '_bottom'] = instance.addEndpoint(id, {
                        //anchor: [ "Perimeter", { shape: "Square" } ],
                        anchor: "Bottom",
                        maxConnections: -1,
                        isSource: true,
                        deleteEndpointsOnDetach: false
                                //isTarget: true
                    });

                    instance.draggable(id, {containment: '#schema-graph', stop: function() {
                            _Schema.storePositions();
                        }});

                });

                if (callback) {
                    callback();
                }

            }
        });
    },
    loadRels: function(callback) {
        var url = rootUrl + 'schema_relationships';
        $.ajax({
            url: url,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            success: function(data) {

                var sId, tId;
                $.each(data.result, function(i, res) {

                    if (sId === res.sourceId && tId === res.targetId) {
                        radius += 10;
                        stub += 30;
                        offset += .1;
                    } else {
                        radius = 20;
                        stub = 30;
                        offset = 0;
                    }

                    sId = res.sourceId;
                    tId = res.targetId;

                    rels[res.id] = instance.connect({
                        source: nodes[sId + '_bottom'],
                        target: nodes[tId + '_top'],
                        deleteEndpointsOnDetach: false,
                        scope: res.id,
                        //parameters: {'id': res.id},
                        connector: [connectorStyle, {curviness: 200, cornerRadius: radius, stub: stub, gap: 6, alwaysRespectStubs: true}],
                        overlays: [
                            ["Label", {
                                    cssClass: "label multiplicity",
                                    label: res.sourceMultiplicity ? res.sourceMultiplicity : '*',
                                    location: .15 + offset,
                                    id: "sourceMultiplicity",
                                    events: {
                                        "click": function(label, evt) {
                                            evt.preventDefault();
                                            var overlay = rels[res.id].getOverlay('sourceMultiplicity');
                                            if (!(overlay.getLabel().substring(0, 1) === '<')) {
                                                overlay.setLabel('<input class="source-mult-label" type="text" size="15" id="id_' + res.id + '_sourceMultiplicity" value="' + overlay.label + '">');
                                                $('.source-mult-label').focus().on('blur', function() {
                                                    var label = ($(this).val() || '').trim();
                                                    _Schema.setRelationshipProperty(res, 'sourceMultiplicity', label);
                                                    overlay.setLabel(label);
                                                });
                                            }
                                        }
                                    }
                                }
                            ],
                            ["Label", {
                                    cssClass: "label rel-type",
                                    label: '<div id="rel_' + res.id + '">' + (res.relationshipType === initialRelType ? '&nbsp;' : res.relationshipType)
                                            + ' <img title="Edit schema relationship" alt="Edit schema relationship" class="edit icon" src="icon/pencil.png">'
                                            + ' <img title="Remove schema relationship" alt="Remove schema relationship" class="remove icon" src="icon/delete.png"></div>',
                                    location: .5 + offset,
                                    id: "label",
                                    events: {
                                        "click": function(label, evt) {
                                            evt.preventDefault();
                                            var overlay = rels[res.id].getOverlay('label');
                                            var l = $(overlay.getLabel()).text().trim();
                                            if ((overlay.getLabel().substring(0, 6) !== '<input')) {
                                                overlay.setLabel('<input class="relationship-label" type="text" size="15" id="id_' + res.id + '_relationshipType" value="' + l + '">');
                                                $('.relationship-label').focus().on('blur', function() {
                                                    var label = ($(this).val() || '').trim();
                                                    _Schema.setRelationshipProperty(res, 'relationshipType', label);
                                                    overlay.setLabel(label);
                                                });
                                            }
                                        }
                                    }
                                }
                            ],
                            ["Label", {
                                    cssClass: "label multiplicity",
                                    label: res.targetMultiplicity ? res.targetMultiplicity : '*',
                                    location: .85 - offset,
                                    id: "targetMultiplicity",
                                    events: {
                                        "click": function(label, evt) {
                                            evt.preventDefault();
                                            var overlay = rels[res.id].getOverlay('targetMultiplicity');
                                            if (!(overlay.getLabel().substring(0, 1) === '<')) {
                                                overlay.setLabel('<input class="target-mult-label" type="text" size="15" id="id_' + res.id + '_targetMultiplicity" value="' + overlay.label + '">');
                                                $('.target-mult-label').focus().on('blur', function() {
                                                    var label = ($(this).val() || '').trim();
                                                    _Schema.setRelationshipProperty(res, 'targetMultiplicity', label);
                                                    overlay.setLabel(label);
                                                });
                                            }
                                        }
                                    }
                                }
                            ]

                        ]
                    });

                    $('#rel_' + res.id).parent().on('mouseover', function(e) {
                        //e.stopPropagation();
                        $('#rel_' + res.id + ' .icon').show();
                    });

                    $('#rel_' + res.id).parent().on('mouseout', function(e) {
                        //e.stopPropagation();
                        $('#rel_' + res.id + ' .icon').hide();
                    });

                    $('#rel_' + res.id + ' .edit').on('click', function(e) {
                        e.stopPropagation();

                        Structr.dialog('Edit schema relationship', function() {
                        }, function() {
                            instance.repaintEverything();
                        });
                        //console.log($(this).parent().prop('id'));
                        var id = getIdFromPrefixIdString($(this).parent().prop('id'), 'rel_');
                        //console.log('Editing relationship', id);
                        Command.getRelationship(id, function(entity) {
                            _Schema.loadRelationship(entity, dialogText);
                        });

                        return false;
                    });

                    $('#rel_' + res.id + ' .remove').on('click', function(e) {
                        e.stopPropagation();
                        Structr.confirmation('<h3>Delete schema relationship?</h3>',
                                function() {
                                    $.unblockUI({
                                        fadeOut: 25
                                    });
                                    _Schema.detach(res.id);
                                    _Schema.reload();
                                });
                        _Schema.reload();
                        return false;
                    });
                });


                if (callback) {
                    callback();
                }

            }
        });
    },
    loadNode: function(entity, el) {
        remotePropertyKeys = [];
        el.append('<div id="___' + entity.id + '" class="schema-details"><b>' + entity.name + '</b>'
                + (entity.isBuiltinType ? '' : ' extends <select class="extends-class-select"><option value="org.structr.core.entity.AbstractNode">org.structr.core.entity.AbstractNode</option></select>')
                + '<h3>Local Attributes</h3><table class="local schema-props"><th>JSON Name</th><th>DB Name</th><th>Type</th><th>Format</th><th>Not null</th><th>Unique</th><th>Default</th><th>Action</th></table>'
                + '<img alt="Add local attribute" class="add-icon add-local-attribute" src="icon/add.png">'
                + '<h3>Methods</h3><table class="actions schema-props"><th>JSON Name</th><th>Code</th><th>Action</th></table>'
                + '<img alt="Add action" class="add-icon add-action-attribute" src="icon/add.png">'
                + '<h3>Views</h3><table class="views schema-props"><th>Name</th><th>Attributes</th><th>Action</th></table>'
                + '<img alt="Add view" class="add-icon add-view" src="icon/add.png">'
                + '<h3>Remote Attributes</h3><table class="related-attrs schema-props"><th>JSON Name</th><th>Type and Direction</th></table>'
                + '</div>');

        var n = $('.schema-details', el);
        n.children('b').on('click', function() {
            _Schema.makeAttrEditable(n, 'name');
        });

        var classSelect = $('.extends-class-select', el);
        $.get(rootUrl + '_schema', function(data) {
            var result = data.result;
            var classNames = [];
            $.each(result, function(t, cls) {
                var type = cls.type;
                var fqcn = cls.className;
                if ( !type || type.startsWith('_') || fqcn.startsWith('org.structr.web.entity.html') || fqcn.endsWith('.' + entity.name) ) {
                    return;
                }
                classNames.push(fqcn);
            });

            classNames.sort();

            classNames.forEach( function (classname) {
                classSelect.append('<option ' + (entity.extendsClass === classname ? 'selected="selected"' : '') + ' value="' + classname + '">' + classname + '</option>');
            });

            classSelect.chosen({ search_contains: true });
        });

        classSelect.on('change', function() {
            var obj = {extendsClass: $(this).val()};
            _Schema.putPropertyDefinition(entity, JSON.stringify(obj));
        });

        _Schema.appendLocalPropertiesAndActions(el, entity);
        _Schema.appendRemoteProperties(el, entity.id);

        var actionsTable = $('.actions.schema-props', el);

        $('.add-action-attribute', el).on('click', function() {
            actionsTable.append('<tr class="new"><td style="vertical-align:top;"><input size="15" type="text" class="action property-name" placeholder="Enter method name"></td>'
                    + '<td><textarea rows="4" class="action property-code" placeholder="Enter Code"></textarea></td><td><img alt="Remove" class="remove-icon remove-action" src="icon/delete.png"></td>'
                    + '</div');

            $('.new .property-code.action', el).on('blur', function() {
                _Schema.saveActionDefinition(entity, 'new');
            });

            $('.new .remove-action', el).on('click', function() {
                var self = $(this);
                self.closest('tr').remove();
            });
        });

        var viewsTable = $('.views.schema-props', el);

        $('.add-view', el).on('click', function() {
            viewsTable.append('<tr class="new"><td><input size="15" type="text" class="view property-name" placeholder="Enter view name"></td>'
                    + '<td><input size="15" type="text" class="view property-attrs" placeholder="Enter view attributes"></td><td><img alt="Remove" class="remove-icon remove-view" src="icon/delete.png"></td>'
                    + '</div');

            $('.new .property-attrs.view', el).on('blur', function() {
                _Schema.saveViewDefinition(entity, 'new');
            });

            $('.new .remove-view', el).on('click', function() {
                var self = $(this);
                self.closest('tr').remove();
            });
        });

    },
    loadRelationship: function(entity, el) {

        el.append('<div id="___' + entity.id + '" class="schema-details"><b>' + entity.relationshipType + '</b>'
                //+ ' extends <select class="extends-class-select"><option value="org.structr.core.entity.AbstractRelationship">AbstractRelationship</option></select>'
                + '<h3>Cascading Delete</h3><select id="cascading-delete-selector"><option value="0">NONE</option><option value="1">SOURCE_TO_TARGET</option><option value="2">TARGET_TO_SOURCE</option><option value="3">ALWAYS</option><option value="4">CONSTRAINT_BASED</option></select>'
                + '<h3>Automatic Creation of Related Nodes</h3><select id="autocreate-selector"><option value="0">NONE</option><option value="1">SOURCE_TO_TARGET</option><option value="2">TARGET_TO_SOURCE</option><option value="3">ALWAYS</option></select>'
                + '<h3>Local Attributes</h3><table class="local schema-props"><th>JSON Name</th><th>DB Name</th><th>Type</th><th>Format</th><th>Not null</th><th>Unique</th><th>Default</th><th>Action</th></table>'
                + '<img alt="Add local attribute" class="add-icon add-local-attribute" src="icon/add.png">'
                + '<h3>Actions</h3><table class="actions schema-props"><th>JSON Name</th><th>Code</th><th>Action</th></table>'
                + '<img alt="Add action" class="add-icon add-action-attribute" src="icon/add.png">'
                + '<h3>Views</h3><table class="views schema-props"><th>Name</th><th>Attributes</th><th>Action</th></table>'
                + '<img alt="Add view" class="add-icon add-view" src="icon/add.png">'
                + '</div>');

        var n = $('.schema-details', el);
        n.children('b').on('click', function() {
            _Schema.makeAttrEditable(n, 'relationshipType', true);
        });

        _Schema.appendLocalPropertiesAndActions(el, entity);

        var actionsTable = $('.actions.schema-props', el);

        $('.add-action-attribute', el).on('click', function() {
            actionsTable.append('<tr class="new"><td style="vertical-align:top;"><input size="15" type="text" class="action property-name" placeholder="Enter method name"></td>'
                    + '<td><textarea rows="4" class="action property-code" placeholder="Enter Code"></textarea></td><td><img alt="Remove" class="remove-icon remove-action" src="icon/delete.png"></td>'
                    + '</div');

            $('.new .property-code.action', el).on('blur', function() {
                _Schema.saveActionDefinition(entity, 'new');
            });

            $('.new .remove-action', el).on('click', function() {
                var self = $(this);
                self.closest('tr').remove();
            });
        });

        var viewsTable = $('.views.schema-props', el);

        $('.add-view', el).on('click', function() {
            viewsTable.append('<tr class="new"><td><input size="15" type="text" class="view property-name" placeholder="Enter view name"></td>'
                    + '<td><input size="15" type="text" class="view property-attrs" placeholder="Enter view attributes"></td><td><img alt="Remove" class="remove-icon remove-view" src="icon/delete.png"></td>'
                    + '</div');

            $('.new .property-attrs.view', el).on('blur', function() {
                _Schema.saveViewDefinition(entity, 'new');
            });

            $('.new .remove-view', el).on('click', function() {
                var self = $(this);
                self.closest('tr').remove();
            });
        });

        $.get(rootUrl + entity.id, function(data) {
            $('#cascading-delete-selector').val(data.result.cascadingDeleteFlag);
        });

        $('#cascading-delete-selector').on('change', function() {
           var inp = $(this);
           _Schema.setRelationshipProperty(entity, 'cascadingDeleteFlag', parseInt(inp.val()),
           function() {
               blinkGreen(inp);
           },
           function() {
               blinkRed(inp);
           });
        });

        $.get(rootUrl + entity.id, function(data) {
            $('#autocreate-selector').val(data.result.autocreationFlag);
        });

        $('#autocreate-selector').on('change', function() {
           var inp = $(this);
           _Schema.setRelationshipProperty(entity, 'autocreationFlag', parseInt(inp.val()),
           function() {
               blinkGreen(inp);
           },
           function() {
               blinkRed(inp);
           });
        });

    },
    appendLocalPropertiesAndActions: function(el, entity, compact) {

        if (compact) {
            el.append(
                    '<h3>Local Attributes</h3><table class="local schema-props"></table>'
                    + '<h3>Methods</h3><table class="actions schema-props"></table>'
                    + '<h3>Views</h3><table class="views schema-props"></table>');
        }

        var propertiesTable = $('.local.schema-props', el);
        var actionsTable = $('.actions.schema-props', el);
        var viewsTable = $('.views.schema-props', el);

        $.each(Object.keys(entity), function(i, key) {
            _Schema.appendLocalProperty(propertiesTable, entity, key, compact);
        });

        $.each(Object.keys(entity), function(i, key) {
            _Schema.appendLocalAction(actionsTable, entity, key, compact);
        });

        $.each(Object.keys(entity), function(i, key) {
            _Schema.appendView(viewsTable, entity, key, compact);
        });

        if (!compact) {

            $('.add-local-attribute', el).on('click', function() {
                propertiesTable.append('<tr class="new"><td><input size="15" type="text" class="property-name" placeholder="Enter JSON name"></td>'
                        + '<td><input size="15" type="text" class="property-dbname" placeholder="Enter DB name"></td>'
                        + '<td>' + typeOptions + '</td>'
                        + '<td><input size="15" type="text" class="property-format" placeholder="Enter format"></td>'
                        + '<td><input class="not-null" type="checkbox"></td>'
                        + '<td><input class="unique" type="checkbox"></td>'
                        + '<td><input class="property-default" size="10" type="text"></td><td><img alt="Remove" class="remove-icon remove-property" src="icon/delete.png"></td></div>');

                $('.new .remove-property', propertiesTable).on('click', function() {
                    var self = $(this);
                    self.closest('tr').remove();
                });

                $('.new .property-name', propertiesTable).on('blur', function() {
                    _Schema.collectAndSaveNewLocalProperty(propertiesTable, entity);
                });

                $('.new .property-type', propertiesTable).on('change', function() {
                    _Schema.collectAndSaveNewLocalProperty(propertiesTable, entity);
                });

                $('.new .property-format', propertiesTable).on('blur', function() {
                    _Schema.collectAndSaveNewLocalProperty(propertiesTable, entity);
                });

                $('.new .not-null', propertiesTable).on('change', function() {
                    _Schema.collectAndSaveNewLocalProperty(propertiesTable, entity);
                });

                $('.new .unique', propertiesTable).on('change', function() {
                    _Schema.collectAndSaveNewLocalProperty(propertiesTable, entity);
                });
            });
        }

    },
    collectAndSaveNewLocalProperty: function(el, entity) {
        var name = $('.new .property-name', el).val();
        var dbName = $('.new .property-dbname', el).val();
        var type = $('.new .property-type', el).val();
        var format = $('.new .property-format', el).val();
        var notNull = $('.new .not-null', el).is(':checked');
        var unique = $('.new .unique', el).is(':checked');
        var defaultValue = $('.new .property-default', el).val();
        if (name && name.length && type) {
            var obj = {};
            var key = '_' + name;
            var val = ''
                    + (dbName ? dbName + '|' : '')
                    + (notNull ? '+' : '')
                    + (type ? type : '')
                    + (unique ? '!' : '')
                    + (format ? '(' + format + ')' : '')
                    + (defaultValue ? ':' + defaultValue : '');
            obj[key] = val;
            _Schema.putPropertyDefinition(entity, JSON.stringify(obj), function() {

                var row = $('.new', el);
                blinkGreen(row);

                _Schema.unbindEvents(key);

                row.removeClass('new').addClass('local').addClass(key);
                row = $('.local.' + key, el);

                $('.remove-property', row).off('click');

                $('.remove-property', row).on('click', function() {
                    _Schema.removeLocalProperty(entity, key);
                });

                entity[key] = val;

                _Schema.bindEvents(entity, type, key);

            }, function() {
                blinkRed($('.new', el));
                _Schema.bindEvents(entity, type, key);
            });
        }
    },
    removeLocalProperty: function(entity, key) {
        var stillUsed = false;
        var normalizedKey = normalizeAttr(key);
        Object.keys(entity).forEach(function(k) {
            if (entity[k] && (typeof entity[k] === 'string')) {

                // check views for usage of this property
                if (k.startsWith('__')) {
                    var viewVars = entity[k].split(',');

                    viewVars.forEach(function(viewVar){

                        if (normalizeAttr(viewVar.trim()) === normalizedKey) {
                            stillUsed = true;
                        };
                    });

                    if (stillUsed) {
                        return;
                    }
                }
            }
        });

        if (stillUsed) {
            $('.local .' + key + ' .remove-icon').tooltip({
                tooltipClass: 'tooltip',
                items: '.remove-icon',
                show: { effect: 'fadeIn', duration: 0 },
                hide: { effect: 'fadeOut', delay: 1000, duration: 150 },
                content: '<img src="/structr/icon/error.png"> The attribute <b>' + normalizedKey + '</b> cannot be removed because it is still referenced somewhere in the schema. Check also the methods, views, notion and function properties.',
                position: { my: "right top", at: "right bottom", collision: "flipfit" },
//                open: function() {
//
//                },
                close: function() {
                    $('.local .' + key + ' .remove-icon').tooltip('disable');
                    window.setTimeout(function() {
                        $('.local .' + key + ' .remove-icon').tooltip('destroy');
                    }, 1160);
                }
            }).tooltip('open');
            return;
        }

        Structr.confirmation('<h3>Delete property ' + key + '?</h3><p>Property values will not be removed from data nodes.</p>',
                function() {
                    $.unblockUI({
                        fadeOut: 25
                    });
                    _Schema.removePropertyDefinition(entity, key);
                });

    },
    appendRemoteProperties: function(el, id, compact) {

        if (compact) {
            el.append('<h3>Remote Attributes</h3><table class="related-attrs schema-props"></table>');
        }

        var url = rootUrl + 'schema_relationships?sourceId=' + id;
        $.ajax({
            url: url,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            success: function(data) {

                $.each(data.result, function(i, res) {

                    var source = nodes[res.sourceId];
                    var target = nodes[res.targetId];

                    _Schema.getPropertyName(source.name, res.relationshipType, target.name, true, function(key) {
                        _Schema.appendRelatedProperty($('.related-attrs', el), res, res.targetJsonName ? res.targetJsonName : key, true);
                        instance.repaintEverything();
                    });

                });

            }
        });

        url = rootUrl + 'schema_relationships?targetId=' + id;
        $.ajax({
            url: url,
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            success: function(data) {

                $.each(data.result, function(i, res) {

                    var source = nodes[res.sourceId];
                    var target = nodes[res.targetId];

                    _Schema.getPropertyName(target.name, res.relationshipType, source.name, false, function(key) {
                        _Schema.appendRelatedProperty($('.related-attrs', el), res, res.sourceJsonName ? res.sourceJsonName : key, false);
                        instance.repaintEverything();
                    });

                });

            }
        });

    },
    resize: function() {

        var w = $(window).width() - 24;
        var h = $(window).height() - 140;

        if (canvas) {
            canvas.css({
                width: w + 'px',
                height: h + 'px'
            });
        }

        $('body').css({
            position: 'relative'
//            background: '#fff'
        });

        $('html').css({
            background: '#fff'
        });

    },
    appendLocalProperty: function(el, res, key, compact) {

        if (key.startsWith('___') || key.startsWith('__')) {
            return false;
        }

        if (key.substring(0, 1) === '_') {

            var property = _Schema.property(res, key);

            if (compact) {

                el.append('<tr class="' + key + '"><td>' + property.name + '</td>'
                        + '<td>' + property.type + '</td>'
                        + '<td>' + (property.format ? escapeForHtmlAttributes(property.format) : '') + '</td></div>');

            } else {

                el.append('<tr class="' + key + '"><td><input size="15" type="text" class="property-name" value="' + escapeForHtmlAttributes(property.name) + '"></td><td>'
                        + '<input size="15" type="text" class="property-dbname" value="' + escapeForHtmlAttributes(property.dbName) + '"></td><td>'
                        + typeOptions + '</td><td><input size="15" type="text" class="property-format" value="'
                        + (property.format ? escapeForHtmlAttributes(property.format) : '') + '"></td><td><input class="not-null" type="checkbox"'
                        + (property.notNull ? ' checked="checked"' : '') + '></td><td><input class="unique" type="checkbox"'
                        + (property.unique ? ' checked="checked"' : '') + '</td><td>'
                        + '<input type="text" size="10" class="property-default" value="' + escapeForHtmlAttributes(property.defaultValue) + '">' + '</td><td><img alt="Remove" class="remove-icon remove-property" src="icon/delete.png"></td></div>');

                _Schema.bindEvents(res, property.type, key);
            }
        }

    },
    property: function(res, key) {

        var name = key.substring(1);
        var unparsed = res[key];

        // dbName
        // NOTE: The format string can likely contain pipes when it is javascript...
        // that's why we are ignoring the pipe if it is after the first plus, colon, bracket, bang, etc
        var dbName = '';
        var locFirstPipe = unparsed.indexOf('|');
        if (locFirstPipe !== -1) {
            dbName = unparsed.substring(0, locFirstPipe);

            if (dbName.indexOf('+') !== -1 || dbName.indexOf('[') !== -1 || dbName.indexOf('!') !== -1 || dbName.indexOf('(') !== -1 || dbName.indexOf(':') !== -1) {
                // ignore dbName
                dbName = '';
            } else {
                unparsed = unparsed.substring(locFirstPipe + 1);
            }
        }


        // notNull
        var notNull = (unparsed[0] === '+');
        if (notNull) {
            unparsed = unparsed.substring(1);
        }


        // read the type (this works by finding the first of the known delimiters and cutting off at that point)
        var type = '';
        var firstDelimLoc = unparsed.length;
        var delims = ['[', '!', '(', ':'];
        delims.forEach(function (d) {
            var loc = unparsed.indexOf(d);
            if (loc !== -1 && firstDelimLoc > loc) {
                firstDelimLoc = loc;
            }
        });
        type = unparsed.substring(0, firstDelimLoc);
        unparsed = unparsed.substring(firstDelimLoc);


        // content-type
        var contentType = '';
        if (unparsed.length > 0 && unparsed[0] === '[') {

            // NOTE: this can break if the content-type contains a ']' which is an allowed character
            var contentTypeEnd = unparsed.indexOf(']');

            contentType = unparsed.substring(1, contentTypeEnd);
            unparsed = unparsed.substring(contentTypeEnd + 1);
        }


        // unique
        var unique = (unparsed.length > 0 && unparsed[0] === '!');
        if (unique) {
            unparsed = unparsed.substring(1);
        }


        // format
        var format = '';
        if (unparsed.length > 0 && unparsed[0] === '(') {
            // NOTE: this can break if the default value contains the string ')'
            // but since format is way more likely to contain ')' we use lastIndexOf
            var formatEnd = unparsed.lastIndexOf(')');

            format = unparsed.substring(1, formatEnd);
            unparsed = unparsed.substring(formatEnd + 1);
        }


        // defaultValue
        var defaultValue = '';
        if (unparsed.length > 0 && unparsed[0] === ':') {
            defaultValue = unparsed.substring(1);
        }

        return { type: type, contentType: contentType, name: name, dbName: dbName, notNull: notNull, unique: unique, defaultValue: defaultValue, format: format };
        
    },
    bindEvents: function(entity, type, key) {

        if (key.substring(0, 1) !== '_') {
            return;
        }
        
        var property = _Schema.property(entity, key);
        
        var el = $('.local.schema-props');

        $('.' + key + ' .property-type option[value="' + type + '"]', el).attr('selected', true).prop('disabled', null);
        
        var typeField = $('.' + key + ' .property-type', el);
        $('.' + key + ' .property-type option[value=""]', el).remove();
        
        if (type === 'String') {
            if (!$('input.content-type', typeField.parent()).length) {
                typeField.after('<input type="text" size="5" class="content-type">');
            }
            $('.' + key + ' .content-type', el).on('blur', function() {
                _Schema.savePropertyDefinition(entity, key);
            }).prop('disabled', null).val(property.contentType);
        }
        
        if (type && type !== '') {
            $('.' + key + ' .property-name', el).on('change', function() {
                _Schema.savePropertyDefinition(entity, key);
            }).prop('disabled', null).val(property.name);
            $('.' + key + ' .property-dbname', el).on('change', function() {
                _Schema.savePropertyDefinition(entity, key);
            }).prop('disabled', null).val(property.dbName);
        }

        $('.' + key + ' .property-type', el).on('change', function() {
            _Schema.savePropertyDefinition(entity, key);
        }).prop('disabled', null).val(property.type);

        $('.' + key + ' .property-format', el).on('blur', function() {
            _Schema.savePropertyDefinition(entity, key);
        }).prop('disabled', null).val(property.format);

        $('.' + key + ' .not-null', el).on('change', function() {
            _Schema.savePropertyDefinition(entity, key);
        }).prop('disabled', null).val(property.notNull);

        $('.' + key + ' .unique', el).on('change', function() {
            _Schema.savePropertyDefinition(entity, key);
        }).prop('disabled', null).val(property.unique);

        $('.' + key + ' .property-default', el).on('change', function() {
            _Schema.savePropertyDefinition(entity, key);
        }).prop('disabled', null).val(property.defaultValue);

        $('.' + key + ' .remove-property', el).on('click', function() {
            _Schema.removeLocalProperty(entity, key);
        }).prop('disabled', null);
        

    },
    unbindEvents: function(key) {

        var el = $('.local.schema-props');

        $('.' + key + ' .property-type', el).off('change').prop('disabled', 'disabled');
        
        $('.' + key + ' .content-type', el).off('change').prop('disabled', 'disabled');

        $('.' + key + ' .property-format', el).off('blur').prop('disabled', 'disabled');

        $('.' + key + ' .not-null', el).off('change').prop('disabled', 'disabled');

        $('.' + key + ' .unique', el).off('change').prop('disabled', 'disabled');

        $('.' + key + ' .property-default', el).off('change').prop('disabled', 'disabled');

        $('.' + key + ' .remove-property', el).off('click').prop('disabled', 'disabled');

    },
    appendRelatedProperty: function(el, rel, key, out) {
        remotePropertyKeys.push('_' + key);
        var relType = rel.relationshipType;
        relType = relType === undefinedRelType ? '' : relType;

        el.append('<tr class="' + key + '"><td><input size="15" type="text" class="property-name related" value="' + key + '"></td><td>'
                + (out ? '-' : '&lt;-') + '[:' + relType + ']' + (out ? '-&gt;' : '-') + '</td></tr>');

        $('.' + key + ' .property-name', el).on('blur', function() {

            var newName = $(this).val();

            if (newName === '') {
                newName = undefined;
            }

            if (out) {
                _Schema.setRelationshipProperty(rel, 'targetJsonName', newName, function() {
                    blinkGreen($('.' + key, el));
                    remotePropertyKeys.push('_' + newName);
                    remotePropertyKeys = without('_' + key, remotePropertyKeys);
                }, function() {
                    blinkRed($('.' + key, el));
                });
            } else {
                _Schema.setRelationshipProperty(rel, 'sourceJsonName', newName, function() {
                    blinkGreen($('.' + key, el));
                    remotePropertyKeys.push('_' + newName);
                    remotePropertyKeys = without('_' + key, remotePropertyKeys);
                }, function() {
                    blinkRed($('.' + key, el));
                });
            }
        });

    },
    appendLocalAction: function(el, res, key, compact) {

        if (key.substring(0, 3) === '___') {

            var name = key.substring(3);
            var value = res[key];
            //var prefix = name.startsWith('onCreate') || name.startsWith('onSave') || name.startsWith('onDelete') ? '-' : '+';

            if (compact) {
                el.append('<tr class="' + key + '"><td>' + name + '</td><td>' + value + '</td></tr>');
            } else {

                // append default actions
                el.append('<tr class="' + key + '"><td style="vertical-align:top;"><input size="15" type="text" class="property-name action" value="'
                        + escapeForHtmlAttributes(name) + '"></td><td><textarea rows="4" class="property-code action">'
                        + escapeForHtmlAttributes(value) + '</textarea></td><td><img alt="Remove" class="remove-icon remove-action" src="icon/delete.png"></td></tr>');

                $('.' + key + ' .property-code.action').on('blur', function() {
                    _Schema.saveActionDefinition(res, key);
                });

                $('.' + key + ' .property-name.action').on('blur', function() {
                    _Schema.saveActionDefinition(res, key);
                });

                $('.' + key + ' .remove-action').on('click', function() {
                    Structr.confirmation('<h3>Delete method ' + key + '?</h3>',
                            function() {
                                $.unblockUI({
                                    fadeOut: 25
                                });
                                _Schema.removeActionDefinition(res, key);
                            });

                });
            }
        }
    },
    appendView: function(el, res, key, compact) {

        if (key.substring(0, 3) === '___') {
            return;
        }

        if (key.startsWith('__') && !key.startsWith('___')) {

            var name = key.substring(2);
            var value = res[key];

            if (compact) {
                el.append('<tr class="' + key + '"><td>' + name + '</td><td>' + value + '</td></tr>');
            } else {

                el.append('<tr class="' + key + '"><td><input size="15" type="text" class="property-name view" value="'
                        + escapeForHtmlAttributes(name) + '"></td><td><input size="30" type="text" class="property-attrs view" value="'
                        + escapeForHtmlAttributes(denormalizeAttrs(value)) + '"></td><td><img alt="Remove" class="remove-icon remove-view" src="icon/delete.png"></td></tr>');

                $('.' + key + ' .property-attrs.view').on('blur', function() {
                    _Schema.saveViewDefinition(res, key);
                });

                $('.' + key + ' .property-name.view').on('blur', function() {
                    _Schema.saveViewDefinition(res, key);
                });

                $('.' + key + ' .remove-view').on('click', function() {
                    Structr.confirmation('<h3>Delete view ' + key + '?</h3>',
                            function() {
                                $.unblockUI({
                                    fadeOut: 25
                                });
                                _Schema.removeActionDefinition(res, key);
                            });

                });
            }
        }
    },
    removePropertyDefinition: function(entity, key) {
        entity[key] = undefined;
        delete entity[key];
        var obj = {};
        obj[key] = null;
        _Schema.putPropertyDefinition(entity, JSON.stringify(obj));
    },
    savePropertyDefinition: function(entity, key) {
        _Schema.unbindEvents(key);
        var name = $('.' + key + ' .property-name').val();
        var dbName = $('.' + key + ' .property-dbname').val();
        var type = $('.' + key + ' .property-type').val();
        var contentType = $('.' + key + ' .content-type').val();
        var format = $('.' + key + ' .property-format').val();
        var notNull = $('.' + key + ' .not-null').is(':checked');
        var unique = $('.' + key + ' .unique').is(':checked');
        var defaultValue = $('.' + key + ' .property-default').val();
        if (name && name.length && type) {
            var obj = {};
            var key = '_' + name;
            var val = (dbName ? dbName + '|' : '')
                    + (notNull ? '+' : '')
                    + (type === 'del' ? null : type)
                    + (contentType ? '[' + contentType + ']' : '')
                    + (unique ? '!' : '')
                    + (format ? '(' + format + ')' : '')
                    + (defaultValue ? ':' + defaultValue : '');
            obj[key] = val;
            _Schema.putPropertyDefinition(entity, JSON.stringify(obj), function() {
                blinkGreen($('.local .' + key));
                entity['_' + name] = val;
                _Schema.bindEvents(entity, type, key);
            }, function() {
                blinkRed($('.local .' + key));
                 _Schema.bindEvents(entity, type, key);
            }, function() {
                _Schema.bindEvents(entity, type, key);
            });
        }
    },
    removeActionDefinition: function(entity, name) {
        var obj = {};
        obj[name] = null;
        _Schema.putPropertyDefinition(entity, JSON.stringify(obj));
    },
    saveActionDefinition: function(entity, key) {
        var name = $('.' + key + ' .action.property-name').val();
        var func = $('.' + key + ' .action.property-code').val();
        if (name && name.length) {
            var obj = {};
            var k = '___' + name;
            var v = (func ? func : '');
            obj[k] = v;
            _Schema.putPropertyDefinition(entity, JSON.stringify(obj),
            function() {
                blinkGreen($('.actions .' + key));
                entity[k] = v;
            },
            function() {
                blinkRed($('.actions .' + key));
            });
        }

    },
    saveViewDefinition: function(entity, key) {
        var keys = Object.keys(entity);
        var name = $('.' + key + ' .view.property-name').val();
        var attrs = $('.' + key + ' .view.property-attrs').val();
        if (name && name.length) {
            attrs = normalizeAttrs(attrs, keys.concat(remotePropertyKeys));
            var obj = {};
            var k = '__' + name;
            var v = (attrs ? attrs : '');
            obj[k] = v;
            _Schema.putPropertyDefinition(entity, JSON.stringify(obj),
            function() {
                blinkGreen($('.views .' + key));
                entity[k] = v;
            },
            function() {
                blinkRed($('.views .' + key));
            });
        }

    },
    putPropertyDefinition: function(entity, data, onSuccess, onError, onNoop) {
        var obj = JSON.parse(data);

        $.ajax({
            url: rootUrl + entity.id,
            type: 'GET',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            statusCode: {
                200: function(existingData) {
                    var changed = false;
                    Object.keys(obj).forEach(function(key) {

                        //console.log('existing value', existingData.result[key], 'new value', obj[key], 'equal?', existingData.result[key] === obj[key]);

                        if (existingData.result[key] !== obj[key]) {
                            changed |= true;
                        }

                    });

                    //console.log('any value changed?', changed);

                    if (changed) {

                        $.ajax({
                            url: rootUrl + entity.id,
                            type: 'PUT',
                            dataType: 'json',
                            contentType: 'application/json; charset=utf-8',
                            data: JSON.stringify(obj),
                            statusCode: {
                                200: function() {
                                    _Schema.reload();
                                    if (onSuccess) {
                                        onSuccess();
                                    }
                                },
                                422: function(data) {
                                    //Structr.errorFromResponse(data.responseJSON);
                                    if (onError) {
                                        onError();
                                    }
                                }
                            }
                        });

                    } else {

                        if (onNoop) {
                            onNoop();
                        }

                    }

                }
            }
        });
    },
    createNode: function(type) {
        var url = rootUrl + 'schema_nodes';
        $.ajax({
            url: url,
            type: 'POST',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify({name: type}),
            statusCode: {
                201: function() {
                    _Schema.reload();
                },
                422: function(data) {
                    Structr.errorFromResponse(data.responseJSON);
                }
            }

        });
    },
    deleteNode: function(id) {
        var url = rootUrl + 'schema_nodes/' + id;
        $.ajax({
            url: url,
            type: 'DELETE',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            statusCode: {
                200: function() {
                    _Schema.reload();
                },
                422: function(data) {
                    Structr.errorFromResponse(data.responseJSON);
                }
            }

        });
    },
    createRelationshipDefinition: function(sourceId, targetId, relationshipType) {
        var data = {
            sourceId: sourceId,
            targetId: targetId,
            sourceMultiplicity: '*',
            targetMultiplicity: '*'
        };
        if (relationshipType && relationshipType.length) {
            data.relationshipType = relationshipType;
        }
        $.ajax({
            url: rootUrl + 'schema_relationships',
            type: 'POST',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            data: JSON.stringify(data),
            statusCode: {
                201: function() {
                    _Schema.reload();
                },
                422: function(data) {
                    Structr.errorFromResponse(data.responseJSON);
                }
            }
        });
    },
    removeRelationshipDefinition: function(id) {
        $.ajax({
            url: rootUrl + 'schema_relationships/' + id,
            type: 'DELETE',
            dataType: 'json',
            contentType: 'application/json; charset=utf-8',
            statusCode: {
                200: function(data, textStatus, jqXHR) {
                    _Schema.reload();
                },
                422: function(data) {
                    Structr.errorFromResponse(data.responseJSON);
                }
            }
        });
    },
    setRelationshipProperty: function(entity, key, value, onSuccess, onError) {
        var data = {};
        data[key] = cleanText(value);
        $.ajax({
            url: rootUrl + 'schema_relationships/' + entity.id,
            type: 'GET',
            contentType: 'application/json; charset=utf-8',
            statusCode: {
                200: function(existingData) {

                    if (existingData.result[key] !== value) {

                        $.ajax({
                            url: rootUrl + 'schema_relationships/' + entity.id,
                            type: 'PUT',
                            dataType: 'json',
                            contentType: 'application/json; charset=utf-8',
                            data: JSON.stringify(data),
                            statusCode: {
                                200: function(data, textStatus, jqXHR) {
                                    _Schema.reload();
                                    if (onSuccess) {
                                        onSuccess();
                                    }
                                },
                                422: function(data) {
                                    //Structr.errorFromResponse(data.responseJSON);
                                    if (onError) {
                                        onError();
                                    }
                                }
                            }
                        });

                    } else {
                        // force a schema-reload so that we dont break the relationships
                        _Schema.reload();
                    }
                }
            }
        });
    },
    connect: function(sourceId, targetId) {
        //Structr.dialog('Enter relationship details');
        _Schema.createRelationshipDefinition(sourceId, targetId, initialRelType);

    },
    detach: function(relationshipId) {
        //Structr.dialog('Enter relationship details');
        _Schema.removeRelationshipDefinition(relationshipId);
    },
    makeAttrEditable: function(element, key, isRel) {
        //element.off('dblclick');

        var id = element.prop('id').substring(3);

        element.off('hover');
        element.children('b').hide();
        var oldVal = $.trim(element.children('b').text());
        var input = $('input.new-' + key, element);

        if (!input.length) {
            element.prepend('<input type="text" size="' + (oldVal.length + 8) + '" class="new-' + key + '" value="' + oldVal + '">');
            input = $('input.new-' + key, element);
        }

        input.show().focus().select();
        input.on('blur', function() {
            if (!id) {
                return false;
            }
            if (isRel) {
                Command.getRelationship(id, function(entity) {
                    _Schema.changeAttr(entity, element, input, key, oldVal, isRel);
                });
            } else {
                Command.get(id, function(entity) {
                    _Schema.changeAttr(entity, element, input, key, oldVal, isRel);
                });
            }
            return false;
        });

        input.keypress(function(e) {
            if (e.keyCode === 13 || e.keyCode === 9) {
                e.preventDefault();
                if (!id) {
                    return false;
                }
                if (isRel) {
                    Command.getRelationship(id, function(entity) {
                        _Schema.changeAttr(entity, element, input, key, oldVal, isRel);
                    });
                } else {
                    Command.get(id, function(entity) {
                        _Schema.changeAttr(entity, element, input, key, oldVal, isRel);
                    });
                }
                return false;
            }
        });
        element.off('click');
    },
    changeAttr: function(entity, element, input, key, oldVal, isRel) {
        var newVal = input.val();
        input.hide();
        element.children('b').text(newVal).show();
        if (oldVal !== newVal) {
            var obj = {};
            obj[key] = newVal;
            if (isRel) {
                _Schema.setRelationshipProperty(entity, key, newVal);
            } else {
                _Schema.putPropertyDefinition(entity, JSON.stringify(obj));
            }

        }
    },
    importGraphGist: function(graphGistUrl, text) {
        $.ajax({
            url: rootUrl + 'maintenance/importGist',
            type: 'POST',
            data: JSON.stringify({'url': graphGistUrl}),
            contentType: 'application/json',
            statusCode: {
                200: function() {
                    var btn = $('#import-ggist');
                    btn.removeClass('disabled').attr('disabled', null);
                    btn.html(text + ' <img src="icon/tick.png">');
                    window.setTimeout(function() {
                        $('img', btn).fadeOut();
                        document.location.reload();
                    }, 1000);
                }
            }
        });
    },
    syncSchemaDialog: function() {

        Structr.dialog('Sync schema to remote server', function() {
        },
                function() {
                });

        var pushConf = JSON.parse(localStorage.getItem(pushConfigKey)) || {};

        dialog.append('To sync <b>all schema nodes and relationships</b> to the remote server, ');
        dialog.append('enter host, port, username and password of your remote instance and click Start.');

        dialog.append('<p><button class="btn" id="pull"">Click here</button> if you want to sync your local schema with schema nodes and relationships from the remote server.</p>');

        $('#pull', dialog).on('click', function(e) {
            e.stopPropagation();
            Structr.pullDialog('SchemaNode,SchemaRelationship');
        });

        dialog.append('<table class="props push">'
                + '<tr><td>Host</td><td><input id="push-host" type="text" length="20" value="' + (pushConf.host || '') + '"></td></tr>'
                + '<tr><td>Port</td><td><input id="push-port" type="text" length="20" value="' + (pushConf.port || '') + '"></td></tr>'
                + '<tr><td>Username</td><td><input id="push-username" type="text" length="20" value="' + (pushConf.username || '') + '"></td></tr>'
                + '<tr><td>Password</td><td><input id="push-password" type="password" length="20" value="' + (pushConf.password || '') + '"></td></tr>'
                + '</table>'
                + '<button id="start-push">Start</button>');



        $('#start-push', dialog).on('click', function() {
            var host = $('#push-host', dialog).val();
            var port = parseInt($('#push-port', dialog).val());
            var username = $('#push-username', dialog).val();
            var password = $('#push-password', dialog).val();
            var key = 'key_push_schema';

            pushConf = {host: host, port: port, username: username, password: password};
            localStorage.setItem(pushConfigKey, JSON.stringify(pushConf));

            Command.pushSchema(host, port, username, password, key, function() {
                dialog.empty();
                dialogCancelButton.click();
            });
        });

        return false;
    },
    openAdminTools: function() {
        Structr.dialog('Admin Tools', function() {
        }, function() {
        });

        dialogText.append('<table id="admin-tools-table">');
        $('#admin-tools-table').append('<tr><td><button id="rebuild-index">Rebuild Index</button></td><td><label for"rebuild-index">Rebuild database index for all nodes and relationships</label></td></tr>');
        $('#admin-tools-table').append('<tr><td><button id="clear-schema">Clear Schema</button></td><td><label for"clear-schema">Delete all schema nodes and relationships of dynamic schema</label></td></tr>');
        $('#admin-tools-table').append('<tr><td><select id="node-type-selector"><option value="">-- Select Node Type --</option></select><!--select id="rel-type-selector"><option>-- Select Relationship Type --</option></select--><button id="add-uuids">Add UUIDs</button></td><td><label for"setUuid">Add UUIDs to all nodes of the selected type</label></td></tr>');
        $('#admin-tools-table').append('</table>');

        var nodeTypeSelector = $('#node-type-selector');

        $('#rebuild-index').on('click', function(e) {
            var btn = $(this);
            var text = btn.text();
            btn.attr('disabled', 'disabled').addClass('disabled').html(text + ' <img src="img/al.gif">');
            e.preventDefault();
            $.ajax({
                url: rootUrl + 'maintenance/rebuildIndex',
                type: 'POST',
                data: {},
                contentType: 'application/json',
                statusCode: {
                    200: function() {
                        var btn = $('#rebuild-index');
                        btn.removeClass('disabled').attr('disabled', null);
                        btn.html(text + ' <img src="icon/tick.png">');
                        window.setTimeout(function() {
                            $('img', btn).fadeOut();
                        }, 1000);
                    }
                }
            });
        });

        $('#clear-schema').on('click', function(e) {

            Structr.confirmation('<h3>Delete schema?</h3><p>This will remove all dynamic schema information, but not your other data.</p><p>&nbsp;</p>',
                    function() {
                        $.unblockUI({
                            fadeOut: 25
                        });

                        var btn = $(this);
                        var text = btn.text();
                        btn.attr('disabled', 'disabled').addClass('disabled').html(text + ' <img src="img/al.gif">');
                        e.preventDefault();
                        $.ajax({
                            url: rootUrl + 'schema_relationships',
                            type: 'DELETE',
                            data: {},
                            contentType: 'application/json',
                            statusCode: {
                                200: function() {
                                    _Schema.reload();
                                    $.ajax({
                                        url: rootUrl + 'schema_nodes',
                                        type: 'DELETE',
                                        data: {},
                                        contentType: 'application/json',
                                        statusCode: {
                                            200: function() {
                                                _Schema.reload();
                                                var btn = $('#clear-schema');
                                                btn.removeClass('disabled').attr('disabled', null);
                                                btn.html(text + ' <img src="icon/tick.png">');
                                                window.setTimeout(function() {
                                                    $('img', btn).fadeOut();
                                                }, 1000);
                                            }
                                        }
                                    });

                                }
                            }
                        });
                    });
        });

        Command.list('SchemaNode', true, 100, 1, 'name', 'asc', 'id,name', function(n) {
            $('#node-type-selector').append('<option>' + n.name + '</option>');
        });

        Command.list('SchemaRelationship', true, 100, 1, 'relationshipType', 'asc', 'id,name', function(r) {
            $('#rel-type-selector').append('<option>' + r.relationshipType + '</option>');
        });

        $('#add-uuids').on('click', function(e) {
            var btn = $(this);
            var text = btn.text();
            e.preventDefault();
            var type = nodeTypeSelector.val();
            var relType = $('#rel-type-selector').val();
            if (!type) {
                nodeTypeSelector.addClass('notify');
                nodeTypeSelector.on('change', function() {
                    nodeTypeSelector.removeClass('notify');
                });
                return;
            }
            btn.attr('disabled', 'disabled').addClass('disabled').html(text + ' <img src="img/al.gif">');
            $.ajax({
                url: rootUrl + 'maintenance/setUuid',
                type: 'POST',
                data: JSON.stringify({'type': type, 'relType': relType}),
                contentType: 'application/json',
                statusCode: {
                    200: function() {
                        var btn = $('#add-uuids');
                        nodeTypeSelector.removeClass('notify');
                        btn.removeClass('disabled').attr('disabled', null);
                        btn.html(text + ' <img src="icon/tick.png">');
                        window.setTimeout(function() {
                            $('img', btn).fadeOut();
                        }, 1000);
                    }
                }
            });
        });

    },
    getPropertyName: function(type, relationshipType, relatedType, out, callback) {
        $.ajax({
            url: rootUrl + '_schema/' + type,
            type: 'GET',
            contentType: 'application/json; charset=utf-8',
            statusCode: {
                200: function(data) {
                    var properties = data.result[0].views.all;
                    Object.keys(properties).forEach(function(key) {
                        var obj = properties[key];
                        var simpleClassName = obj.className.split('.')[obj.className.split('.').length - 1];
                        if (obj.relatedType && obj.relationshipType) {
                            if (obj.relatedType.endsWith(relatedType) && obj.relationshipType === relationshipType && ((simpleClassName.startsWith('EndNode') && out)
                                    || (simpleClassName.startsWith('StartNode') && !out))) {
                                callback(key, obj.isCollection);
                            }

                        }
                    });
                }
            }
        });

    },
    toggleView: function(id) {

        var mode = _Schema.getMode(id);

        if (mode === 'compact') {

            _Schema.expandView(id);
            _Schema.storeMode(id, 'expanded');

        } else {

            _Schema.compactView(id);
            _Schema.storeMode(id, 'compact');
        }

        instance.repaintEverything();

    },
    compactView: function(id) {

        var node = $('#id_' + id);

        if (id.contains('_')) {
            return;
        }

        $('.toggle-view', node).attr('src', 'icon/arrow_out.png');

        $('h3', node).remove();
        $('.schema-props', node).remove();

        node.addClass('compact');
        node.removeClass('expanded');

    },
    expandView: function(id) {

        if (!id || id.contains('_')) {
            return;
        }

        var node = $('#id_' + id);
        $('.toggle-view', node).attr('src', 'icon/arrow_in.png');

        Command.get(id, function(entity) {
            _Schema.appendLocalPropertiesAndActions(node, entity, true);
            _Schema.appendRemoteProperties(node, entity.id, true);
            instance.repaintEverything();
        });

        node.addClass('expanded');
        node.removeClass('compact');
    },
    doLayout: function() {

        var nodesToLayout = new Array();
        var relsToLayout = new Array();

        $.each(Object.keys(nodes), function(i, id) {

            if (!id.endsWith('_top') && !id.endsWith('_bottom')) {

                var node = $('#id_' + id);
                nodesToLayout.push(node);
            }
        });

        $.each(Object.keys(rels), function(i, id) {
            relsToLayout.push(rels[id]);
        });

        _Layout.doLayout(nodesToLayout, relsToLayout);
    }
};

function normalizeAttr(attr) {
    return attr.replace(/^_+/, '');
}

function normalizeAttrs(attrs, keys) {
    return attrs.replace(/ /g, '').split(',').map(function(attr) {
        var a = normalizeAttr(attr);
        if (keys.indexOf('_' + a) !== -1) {
            return '_' + a;
        }
        return a;
    }).join(',');
}

function denormalizeAttrs(attrs) {
    return attrs.replace(/ /g, '').split(',').map(function(attr) {
        var a = normalizeAttr(attr);
        return a;
    }).join(', ');
}

var typeOptions = '<select class="property-type"><option value="">--Select--</option>'
        + '<option value="String">String</option>'
        + '<option value="Integer">Integer</option>'
        + '<option value="Long">Long</option>'
        + '<option value="Double">Double</option>'
        + '<option value="Boolean">Boolean</option>'
        + '<option value="Enum">Enum</option>'
        + '<option value="Date">Date</option>'
        + '<option value="Count">Count</option>'
        + '<option value="Function">Function</option>'
        + '<option value="Notion">Notion</option>'
        + '<option value="Join">Join</option>'
        + '<option value="Cypher">Cypher</option>';

