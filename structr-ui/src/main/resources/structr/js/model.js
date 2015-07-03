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

var StructrModel = {
	objects: {},
	callbacks: [],
	obj: function(id) {
		return StructrModel.objects[id];
	},

	ensureObject: function (entity) {
		if (!entity || entity.id === undefined) {
			return false;
		}
		return StructrModel.obj(entity.id);
	},

	createSearchResult: function(data) {

		var obj = new StructrSearchResult(data);

		// Store a reference of this object
		StructrModel.objects[data.id] = obj;

		// Check if the object is already contained in page
		var el = $('#id_' + obj.id);
		if (el && el.length) {
			return obj;
		}

		StructrModel.append(obj);

		return obj;

	},
	/**
	 * Create a new object in the model and potentially append a UI element
	 * If refId is set, insert before this node
	 */
	create: function(data, refId, append) {

		log("StructrModel.create", data);
		if (!data) return;

		var type = data.type;

		var obj;

		if (data.isPage) {

			obj = new StructrPage(data);

		} else if (data.isWidget) {

			obj = new StructrWidget(data);

		} else if (data.isContent) {

			obj = new StructrContent(data);

		} else if (data.isResourceAccess) {

			obj = new StructrResourceAccess(data);

		} else if (data.isGroup) {

			obj = new StructrGroup(data);

		} else if (data.isUser) {

			obj = new StructrUser(data);

		} else if (data.isImage) {

			obj = new StructrImage(data);

		} else if (data.isFolder) {

			obj = new StructrFolder(data);

		} else if (data.isFile) {

			obj = new StructrFile(data);

		} else {

			obj = new StructrElement(data);

		}

		// Store a reference of this object
		StructrModel.objects[data.id] = obj;

		// Check if the object is already contained in page
		if (obj) {

			if (obj.exists && obj.exists()) {
				var el = Structr.node(obj.id);
				if (el.parent().prop('id') === 'elementsArea') {
					el.remove();
				} else {
					log('obj exists');
					return obj;
				}
			}

		}

		if (refId || append === undefined || append) {
			StructrModel.append(obj, refId);
		}

		return obj;

	},
	/**
	 * Append and check expand status
	 */
	append: function(obj, refId) {

		if (obj.content) {
			// only show the first 40 characters for content elements
			obj.content = obj.content.substring(0, 40);
		}

		var refNode = refId ? Structr.node(refId) : undefined;

		// Display in page (before refNode, if given)
		obj.append(refNode);

	},
	/**
	 * Check expand status
	 */
	expand: function(element, obj) {

		log('StructrModel.expand', element, obj);

		if (element) {

			if (isExpanded(obj.id)) {
				_Entities.ensureExpanded(element);
			}

			var parent = element.parent();

			if (parent && parent.hasClass('node') && parent.children('.node') && parent.children('.node').length) {

				log('parent of last appended object has children');

				var ent = Structr.entityFromElement(parent);
				_Entities.ensureExpanded(parent);
				log('entity', ent);
				_Entities.appendExpandIcon(parent, ent, true, true);

			}
		}
	},
	/**
	 * Deletes an object from the UI.
	 *
	 * If object is page, remove preview and tab. If tab was the active tab,
	 * activate the tab to the left before removing it.
	 */
	del: function(id) {

		var node = Structr.node(id);
		if (node) {
			node.remove();
		}

		if (lastMenuEntry === 'pages') {
			removeExpandedNode(id);
			var iframe = $('#preview_' + id);
			var tab = $('#show_' + id);

			if (id === activeTab) {
				_Pages.activateTab(tab.prev());
			}

			tab.remove();
			iframe.remove();

			_Pages.reloadPreviews();
		}
		if (engine) {
			engine.refresh();
		}

	},
	/**
	 * Update the model with the given data.
	 *
	 * This function is usually triggered by a websocket message
	 * and will trigger a UI refresh.
	 **/
	update: function(data) {
		var obj = StructrModel.obj(data.id);

		if (obj && data.modifiedProperties && data.modifiedProperties.length) {

			$.each(data.modifiedProperties, function(i, key) {
				log('update model', key, data.data[key]);
				obj[key] = data.data[key];
				//console.log('object ', obj, 'updated with key', key, '=', obj[key]);
				//StructrModel.refreshKey(obj.id, key);
			});

			StructrModel.refresh(obj.id);

		}

		return obj;

	},
	updateKey: function(id, key, value) {
		log('StructrModel.updateKey', id, key, value);
		var obj = StructrModel.obj(id);

		if (obj) {
			obj[key] = value;
		}

		//StructrModel.refreshKey(id, key);

	},
	/**
	 * Refresh the object's UI representation with
	 * the current model value for the given key
	 */
	refreshKey: function(id, key, width) {

		var w = width || 200;

		var obj = StructrModel.obj(id);
		if (!obj)
			return;

		var element = Structr.node(id);

		if (!element)
			return;

		//for (var key in data.data) {
		var inputElement = $('td.' + key + '_ input', element);
		log(inputElement);
		var newValue = obj[key];
		//console.log(key, newValue, typeof newValue);

		var attrElement = element.children('.' + key + '_');

		if (attrElement && $(attrElement).length) {
			var tag = $(attrElement).get(0).tagName.toLowerCase();

			if (typeof newValue === 'boolean') {

				_Entities.changeBooleanAttribute(attrElement, newValue);

			} else {

				blinkGreen(attrElement);

				if (attrElement && tag === 'select') {
					attrElement.val(newValue);
				} else {
					log(key, newValue);
					if (key === 'name') {
						attrElement.html(fitStringToWidth(newValue, w));
						attrElement.attr('title', newValue);
					}
				}

				if (inputElement) {
					inputElement.val(newValue);
				}

				if (key === 'content') {

					log(attrElement.text(), newValue);

					attrElement.text(newValue);

					// hook for CodeMirror edit areas
					//                        if (editor && editor.id == id) {
					//                            log(editor.id);
					//                            editor.setValue(newValue);
					//                            editor.setCursor(editorCursor);
					//                        }
				}
			}
		}

		log(key, Structr.getClass(element));

		if (key === 'name' && Structr.getClass(element) === 'page') {

			// update tab and reload iframe
			var tabNameElement = $('#show_' + id).children('.name_');

			blinkGreen(tabNameElement);

			tabNameElement.html(fitStringToWidth(newValue, w));
			tabNameElement.attr('title', newValue);

			log('Model: Reload iframe', id, newValue);
			_Pages.reloadIframe(id)
		}

	},
	/**
	 * Refresh the object's UI representation
	 * with the current object data from the model.
	 */
	refresh: function(id) {

		var obj = StructrModel.obj(id);
		log('Model refresh, updated object', obj);

		if (obj) {
			var element = Structr.node(id);

			if (engine) {
				// Graph display is active
				var node = engine.graph.nodes(obj.id);
				if (node) {
					_Graph.updateNode(node, obj);
				}
			}

			if (!element)
				return;

			log(obj, id, element);

			// update values with given key
			$.each(Object.keys(obj), function(i, key) {
				StructrModel.refreshKey(id, key);
			});

			// update HTML 'class' and 'id' attributes
			if (isIn('_html_id', Object.keys(obj)) || isIn('_html_class', Object.keys(obj))) {

				var classIdAttrsEl = $(element).children('.class-id-attrs');
				if (classIdAttrsEl.length) {
					classIdAttrsEl.remove();
				}

				var classIdString = _Elements.classIdString(obj._html_id, obj._html_class);
				var idEl = $(element).children('.id');
				if (idEl.length) {
					$(element).children('.id').after(classIdString);
				}
			}

			// update icon
			var icon = undefined;
			if ($(element).hasClass('element')) {
				var isComponent = obj.sharedComponent || (obj.syncedNodes && obj.syncedNodes.length);
				var isActiveNode = obj.hideOnIndex || obj.hideOnDetail || obj.hideConditions || obj.showConditions || obj.dataKey;
				icon = isActiveNode ? _Elements.icon_repeater : isComponent ? _Elements.icon_comp : _Elements.icon;
			} else if ($(element).hasClass('file')) {
				icon = _Files.getIcon(obj);
			}
			var iconEl = $(element).children('.typeIcon');
			if (icon && iconEl.length) {
				iconEl.attr('src', icon);
			}

			// check if key icon needs to be displayed (in case of nodes not visible to public/auth users)
			var protected = !obj.visibleToPublicUsers || !obj.visibleToAuthenticatedUsers;
			var keyIcon = $(element).children('.key_icon');
			if (!keyIcon.length) {
				// Images have a special subnode containing the icons
				keyIcon = $('.icons', $(element)).children('.key_icon');
			}
			if (protected) {
				keyIcon.show();
				keyIcon.addClass('donthide');
			} else {
				keyIcon.hide();
				keyIcon.removeClass('donthide');
			}

			var displayName = getElementDisplayName(obj);

			// Did name change from null?
			if ((obj.type === 'Template' || obj.isContent)) {
				if (obj.name) {
					$(element).children('.content_').replaceWith('<b title="' + displayName + '" class="tag_ name_">' + displayName + '</b>');
					$(element).children('.name_').replaceWith('<b title="' + displayName + '" class="tag_ name_">' + displayName + '</b>');
				} else {
					$(element).children('.name_').replaceWith('<div class="content_">' + escapeTags(obj.content) + '</div>');
				}
			} else {
				$(element).children('.name_').replaceWith('<b title="' + displayName + '" class="tag_ name_">' + displayName + '</b>');
			}

		}

	},
	/**
	 * Fetch data from server. This will trigger a refresh of the model.
	 */
	fetch: function(id) {
		Command.get(id);
	},
	/**
	 * Save model data to server. This will trigger a refresh of the model.
	 */
	save: function(id) {
		var obj = StructrModel.obj(id);
		log('StructrModel.save', obj);

		// Filter out object type data
		var data = {};
		$.each(Object.keys(obj), function(i, key) {

			var value = obj[key];

			if (typeof value !== 'object') {
				data[key] = value;
			}

		});
		//console.log('save', id, data);
		Command.setProperties(id, data);
	},

	callCallback: function(callback, entity, resultSize) {
		log('Calling callback', callback, 'on entity', entity, resultSize);
		var callbackFunction = StructrModel.callbacks[callback];
		if (callback && callbackFunction) {
			log(callback, callbackFunction.toString());
			StructrModel.callbacks[callback](entity, resultSize);
		}

	},

	clearCallback : function(callback) {
		if (callback && StructrModel.callbacks[callback]) {
			delete StructrModel.callbacks[callback];
			callback = undefined;
			delete callback;
		}
	}

}


/**************************************
 * Structr Folder
 **************************************/

function StructrFolder(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrFolder.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrFolder.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

StructrFolder.prototype.remove = function() {

	var folder = this;
	var parentFolder = StructrModel.obj(folder.parent.id);
	var parentFolderEl = Structr.node(parentFolder.id);

	if (!parentFolderEl)
		return;

	parentFolder.folders = removeFromArray(parentFolder.folders, folder);

	if (!parentFolder.files.length && !parentFolder.folders.length) {
		_Entities.removeExpandIcon(parentFolderEl);
		//enable(parentFolderEl.children('.delete_icon')[0]);
	}

	var folderEl = Structr.node(folder.id);
	if (!folderEl)
		return;

	_Entities.resetMouseOverState(folderEl);

	folderEl.children('.delete_icon').replaceWith('<img title="Delete folder ' + folder.id + '" '
			+ 'alt="Delete folder ' + folder.id + '" class="delete_icon button" src="' + Structr.delete_icon + '">');

	folderEl.children('.delete_icon').on('click', function(e) {
		e.stopPropagation();
		_Entities.deleteNode(this, folder, true);
	});

	folders.append(folderEl);

}

StructrFolder.prototype.append = function(refNode) {
	StructrModel.expand(_Files.appendFolderElement(this, refNode), this);
}


/**************************************
 * Structr File
 **************************************/

function StructrFile(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrFile.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrFile.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, false, callback);
}

StructrFile.prototype.remove = function() {
	var file = this;

	if (file.parent) {

		var parentFolder = StructrModel.obj(file.parent.id);
		var parentFolderEl = Structr.node(parentFolder.id);

		parentFolder.files = removeFromArray(parentFolder.files, file);
		if (!parentFolder.files.length && !parentFolder.folders.length) {
			_Entities.removeExpandIcon(parentFolderEl);
		}

		file.parent = undefined;
	}

	var fileEl = Structr.node(file.id);
	if (!fileEl) {
		return;
	} else {
		fileEl.remove();
	}

	_Files.appendFileElement(this);

}

StructrFile.prototype.append = function() {
	var file = this;
	if (file.parent) {
		var parentFolder = StructrModel.obj(file.parent.id);
		if (parentFolder) {
			if (!parentFolder.files) {
				parentFolder.files = [];
			}
			parentFolder.files.push(file);
		}
	}
	StructrModel.expand(_Files.appendFileElement(this, parentFolder), this);
}


/**************************************
 * Structr Image
 **************************************/

function StructrImage(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrImage.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrImage.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, false, callback);
}

StructrImage.prototype.remove = function() {
	var file = this;

	if (file.parent) {

		var parentFolder = StructrModel.obj(file.parent.id);
		var parentFolderEl = Structr.node(parentFolder.id);

		parentFolder.files = removeFromArray(parentFolder.files, file);
		if (!parentFolder.files.length && !parentFolder.folders.length) {
			_Entities.removeExpandIcon(parentFolderEl);
			enable(parentFolderEl.children('.delete_icon')[0]);
		}

		file.parent = undefined;
	}

	var fileEl = Structr.node(file.id);
	if (!fileEl) {
		return;
	} else {
		fileEl.remove();
	}

	_Files.appendFileElement(this);
}

StructrImage.prototype.append = function(refNode) {
	var image = this;
	if (image.parent) {
		var parentFolder = StructrModel.obj(image.parent.id);
		if (parentFolder) parentFolder.files.push(image);
	}
	if (images && images.length) {
		StructrModel.expand(_Images.appendImageElement(this, parentFolder), this);
	} else {
		StructrModel.expand(_Files.appendFileElement(this, parentFolder || refNode), this);
	}
}


/**************************************
 * Structr User
 **************************************/

function StructrUser(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrUser.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrUser.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, false, callback);
}

StructrUser.prototype.remove = function() {
	var user = this;

	var group = user.groups[0];
	var groupEl = Structr.node(group.id);

	user.groups = removeFromArray(user.groups, group);

	group.members = removeFromArray(group.members, user);
	if (!group.members.length) {
		_Entities.removeExpandIcon(groupEl);
		if (groupEl && groupEl.length) {
			enable(groupEl.children('.delete_icon')[0]);
		}
	}

	var userEl = Structr.node(user.id);
	if (!userEl) {
		return;
	} else {
		userEl.remove();
	}

	_Security.appendUserElement(this);
}

StructrUser.prototype.append = function() {
	var user = this;
	//console.log(user.groups);
	if (user.groups && user.groups.length) {
		var group = StructrModel.obj(user.groups[0]);
		if (group && group.members) {
			group.members.push(user.id);
		}
	}
	StructrModel.expand(_Security.appendUserElement(this, group), this);
}

/**************************************
 * Structr Group
 **************************************/

function StructrGroup(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrGroup.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrGroup.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

StructrGroup.prototype.append = function(refNode) {
	StructrModel.expand(_Security.appendGroupElement(this, refNode), this);
}

/**************************************
 * Structr ResourceAccess
 **************************************/

function StructrResourceAccess(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrResourceAccess.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrResourceAccess.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

StructrResourceAccess.prototype.append = function(refNode) {
	StructrModel.expand(_Security.appendResourceAccessElement(this, refNode), this);
}

/**************************************
 * Structr Page
 **************************************/

function StructrPage(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

//StructrPage.prototype.createElement = function(name) {
//    return new Element(name);
//}

StructrPage.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

StructrPage.prototype.append = function() {
	StructrModel.expand(_Pages.appendPageElement(this), this);
}

/**************************************
 * Structr Widget
 **************************************/

function StructrWidget(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

//StructrPage.prototype.createElement = function(name) {
//    return new Element(name);
//}

StructrWidget.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

StructrWidget.prototype.append = function() {
	StructrModel.expand(_Widgets.appendWidgetElement(this), this);
}

/**************************************
 * Structr Element
 **************************************/

function StructrElement(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrElement.prototype.appendChild = function(el) {
	var self = this;
	self.children.push(el);
}

StructrElement.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

StructrElement.prototype.removeAttribute = function(key) {
	var self = this;
	delete self[key];
}

StructrElement.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrElement.prototype.remove = function() {
	var element = Structr.node(this.id);
	if (this.parent) {
		var parent = Structr.node(this.parent.id);
	}

	if (element) {
		// If element is removed from page tree, reload elements area
		if (element.closest('#pages').length) {
			_Elements.reloadUnattachedNodes();
		}
		element.remove();
	}

	log(this, element, parent, Structr.containsNodes(parent));

	if (element && parent && !Structr.containsNodes(parent)) {
		_Entities.removeExpandIcon(parent);
	}
	_Pages.reloadPreviews();
}

StructrElement.prototype.append = function(refNode) {
	StructrModel.expand(_Pages.appendElementElement(this, refNode), this);
}

StructrElement.prototype.exists = function() {

	var obj = this;

	var hasChildren = obj.childrenIds && obj.childrenIds.length;
	var isComponent = obj.syncedNodes && obj.syncedNodes.length;

	var isMasterComponent = (isComponent && hasChildren);

	return !isMasterComponent && Structr.node(obj.id);
}


/**************************************
 * Structr Content
 **************************************/

function StructrContent(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

StructrContent.prototype.appendChild = function(el) {
	var self = this;
	self.children.push(el);
}

StructrContent.prototype.setProperty = function(key, value, recursive, callback) {
	Command.setProperty(this.id, key, value, recursive, callback);
}

//StructrContent.prototype.setProperties = function(attributes) {
//    this.attributes = attributes;
//}

StructrContent.prototype.removeAttribute = function(key) {
	var self = this;
	delete self[key];
}

StructrContent.prototype.save = function() {
	StructrModel.save(this.id);
}

StructrContent.prototype.remove = function() {
	var element = Structr.node(this.id);
	if (this.parent) {
		var parent = Structr.node(this.parent.id);
	}

	if (element) {
		// If element is removed from page tree, reload elements area
		if (element.closest('#pages').length) {
			_Elements.reloadUnattachedNodes();
		}
		element.remove();
	}

	if (parent && !Structr.containsNodes(parent)) {
		_Entities.removeExpandIcon(parent);
	}
	_Pages.reloadPreviews();
}

StructrContent.prototype.append = function(refNode) {

	var id = this.id;
	var parentId;

	var parent;
	if (this.parent) {
		parentId = this.parent.id;
		parent = Structr.node(parentId);
	}

	var div = _Contents.appendContentElement(this, refNode);
	if (!div)
		return;

	log('appendContentElement div', div);

	StructrModel.expand(div, this);

	if (parent) {

		$('.button', div).on('mousedown', function(e) {
			e.stopPropagation();
		});

		$('.delete_icon', div).replaceWith('<img title="Remove content element from parent ' + parentId + '" '
				+ 'alt="Remove content element from parent ' + parentId + '" class="delete_icon button" src="' + _Contents.delete_icon + '">');
		$('.delete_icon', div).on('click', function(e) {
			e.stopPropagation();
			Command.removeChild(id);
		});
	}

	_Entities.setMouseOver(div);

}
StructrContent.prototype.exists = function() {

	return Structr.node(this.id);
}

/**************************************
 * Search result
 **************************************/

function StructrSearchResult(data) {
	var self = this;
	$.each(Object.keys(data), function(i, key) {
		self[key] = data[key];
	});
}

//StructrSearchResult.prototype.save = function() {
//    StructrModel.save(this.id);
//}
//
//StructrSearchResult.prototype.setProperty = function(key, value, recursive, callback) {
//    Command.setProperty(this.id, key, value, recursive, callback);
//}
//
StructrSearchResult.prototype.append = function() {

	var obj = this;
	//console.log(obj);

	if (obj.hasOwnProperty('relType') && obj.hasOwnProperty('sourceId') && obj.hasOwnProperty('targetId')) {
		_Graph.drawRel(obj);
	} else {
		_Graph.drawNode(this);
	}
};



function removeFromArray(array, obj) {
	var newArray = [];
	if (array && array.length) {
		$.each(array, function(i, el) {
			if (el.id !== obj.id && el !== obj.id) {
				newArray.push(el);
			}
		});
	}
	return newArray;
}
