import ast.AST_NODE_TYPE;
import ast.ast_node;
import ast.ast_sent;

import common.GlobalMembersGm_misc;
import common.gm_apply;

//----------------------------------------------------------------------
// routines for degugging
//----------------------------------------------------------------------

// print rw info per sentence
// (need to make it sentence block, such as then-clause, for best print-out)
public class gm_print_rw_info extends gm_apply
{
	private int _tab;
	private void print_tab(int j)
	{
		for (int i = 0; i <= j; i++)
			System.out.print("..");
	}
	private void print_set(String c, java.util.HashMap<gm_symtab_entry, java.util.LinkedList<gm_rwinfo>> m)
	{
		print_tab(_tab);
		System.out.printf(" <%s>", c);
		java.util.Iterator<gm_symtab_entry, java.util.LinkedList<gm_rwinfo>> it;
		int cnt2 = 0;
		for (it = m.iterator(); it.hasNext(); it++, cnt2++)
		{
			gm_symtab_entry e = it.next().getKey();
			java.util.LinkedList<gm_rwinfo> l = it.next().getValue();
			assert e != null;
			assert l != null;
			if ((cnt2 % 8) == 7)
			{
				System.out.print("\n");
				print_tab(_tab + 1);
			}
			if (it != m.iterator())
				System.out.print(",");

			if (e.getType().is_property())
				System.out.printf("{%s(%s):", e.getId().get_orgname(), e.getType().get_target_graph_id().get_orgname());
			else
				System.out.printf("{%s:", e.getId().get_orgname());

			java.util.Iterator<gm_rwinfo> ii;
			for (ii = l.iterator(); ii.hasNext();)
			{
				if (ii != l.iterator())
					System.out.print(",");
				(ii.next()).print();
			}

			System.out.print("}");
		}
		System.out.print("\n");
	}
	public gm_print_rw_info()
	{
		_tab = 0;
	}
	@Override
	public boolean apply(ast_sent s)
	{
		if (s.get_nodetype() == AST_NODE_TYPE.AST_SENTBLOCK)
		{
			_tab--;
		}

		if ((s.get_parent() != null) && ((s.get_parent().get_nodetype() != AST_NODE_TYPE.AST_SENTBLOCK) && (s.get_parent().get_nodetype() != AST_NODE_TYPE.AST_PROCDEF)))
		{
			_tab++;
		}
		gm_rwinfo_sets sets = GlobalMembersGm_rw_analysis.get_rwinfo_sets(s);
//C++ TO JAVA CONVERTER WARNING: The following line was determined to be a copy constructor call - this should be verified and a copy constructor should be created if it does not yet exist:
//ORIGINAL LINE: java.util.HashMap<gm_symtab_entry*, java.util.LinkedList<gm_rwinfo*>*>& R = sets->read_set;
		java.util.HashMap<gm_symtab_entry, java.util.LinkedList<gm_rwinfo>> R = new java.util.HashMap(sets.read_set);
//C++ TO JAVA CONVERTER WARNING: The following line was determined to be a copy constructor call - this should be verified and a copy constructor should be created if it does not yet exist:
//ORIGINAL LINE: java.util.HashMap<gm_symtab_entry*, java.util.LinkedList<gm_rwinfo*>*>& W = sets->write_set;
		java.util.HashMap<gm_symtab_entry, java.util.LinkedList<gm_rwinfo>> W = new java.util.HashMap(sets.write_set);
//C++ TO JAVA CONVERTER WARNING: The following line was determined to be a copy constructor call - this should be verified and a copy constructor should be created if it does not yet exist:
//ORIGINAL LINE: java.util.HashMap<gm_symtab_entry*, java.util.LinkedList<gm_rwinfo*>*>& D = sets->reduce_set;
		java.util.HashMap<gm_symtab_entry, java.util.LinkedList<gm_rwinfo>> D = new java.util.HashMap(sets.reduce_set);

		print_tab(_tab);
		System.out.printf("[%s]\n", GlobalMembersGm_misc.gm_get_nodetype_string(s.get_nodetype()));
		if (R.size() > 0)
			print_set("R", R);
		if (W.size() > 0)
			print_set("W", W);
		if (D.size() > 0)
			print_set("D", D);

		if (s.get_nodetype() == AST_NODE_TYPE.AST_SENTBLOCK)
		{
			_tab++;
		}
		if ((s.get_parent() != null) && ((s.get_parent().get_nodetype() != AST_NODE_TYPE.AST_SENTBLOCK) && (s.get_parent().get_nodetype() != AST_NODE_TYPE.AST_PROCDEF)))
		{
			_tab--;
		}
		return true;
	}
	@Override
	public void begin_context(ast_node n)
	{
		_tab++;
	} //printf("XXXX:%d\n",_tab);}
	@Override
	public void end_context(ast_node n)
	{
		_tab--;
	} //printf("YYYY\n");}
}