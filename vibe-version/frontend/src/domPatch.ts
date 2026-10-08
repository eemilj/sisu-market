/*
 * Keeps the app alive when something else edits the page's DOM — most commonly Chrome's built-in page
 * translation, which this Finnish UI invites: it replaces React's text nodes with its own <font> elements.
 * When React later removes or inserts relative to one of its original nodes, that node is no longer where
 * React left it, the browser throws NotFoundError, and React unmounts the entire app: a blank page.
 *
 * This makes the two affected DOM methods tolerate nodes that were moved by someone else (the workaround
 * from facebook/react#11538). It changes nothing when the DOM is as React left it.
 */
if (typeof Node === 'function' && Node.prototype) {
  const originalRemoveChild = Node.prototype.removeChild;
  Node.prototype.removeChild = function <T extends Node>(this: Node, child: T): T {
    if (child.parentNode !== this) {
      // Already moved or replaced (e.g. by a translator): there is nothing left here to remove.
      if (import.meta.env.DEV) console.warn('Ohitettiin poisto: solmua on muokattu sivun ulkopuolelta', child);
      return child;
    }
    return originalRemoveChild.call(this, child) as T;
  };

  const originalInsertBefore = Node.prototype.insertBefore;
  Node.prototype.insertBefore = function <T extends Node>(this: Node, newNode: T, referenceNode: Node | null): T {
    if (referenceNode && referenceNode.parentNode !== this) {
      // The reference node was moved away; append instead of failing.
      if (import.meta.env.DEV) console.warn('Viitesolmua on muokattu sivun ulkopuolelta', referenceNode);
      return originalInsertBefore.call(this, newNode, null) as T;
    }
    return originalInsertBefore.call(this, newNode, referenceNode) as T;
  };
}

export {};
