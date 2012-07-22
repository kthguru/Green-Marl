package common;

import frontend.SYMTAB_TYPES;
import frontend.gm_scope;
import frontend.gm_symtab;

// replace old_scope -> top_scope
public class replace_upper_scope extends gm_apply
{
	private gm_scope old_scope;
	private gm_scope new_scope;

	public replace_upper_scope()
	{
		set_for_symtab(true);
		old_scope = null;
		new_scope = null;
	}
	public final void set_old_scope(gm_scope g)
	{
		old_scope = g;
	}
	public final void set_new_scope(gm_scope g)
	{
		new_scope = g;
	}

	@Override
	public boolean apply(gm_symtab b, SYMTAB_TYPES symtab_type)
	{
		if ((symtab_type == SYMTAB_TYPES.GM_SYMTAB_ARG) || (symtab_type == SYMTAB_TYPES.GM_SYMTAB_VAR))
		{
			if (b.get_parent() == old_scope.get_varsyms())
				b.set_parent(new_scope.get_varsyms());
		}
		else if (symtab_type == SYMTAB_TYPES.GM_SYMTAB_FIELD)
		{
			if (b.get_parent() == old_scope.get_fieldsyms())
				b.set_parent(new_scope.get_fieldsyms());
		}
		else if (symtab_type == SYMTAB_TYPES.GM_SYMTAB_PROC)
		{
			if (b.get_parent() == old_scope.get_procsyms())
				b.set_parent(new_scope.get_procsyms());
		}
		else
		{
			assert false;
		}
		return true;
	}
}